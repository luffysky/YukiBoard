#!/usr/bin/env python3
"""
FraudAI Model Training Script
===============================
Fine-tunes bert-base-chinese on synthetic fraud detection data,
then exports to ONNX and TFLite (INT8 quantized, < 30MB).

Can run as a regular Python script or in Google Colab.

Usage:
    python scripts/train_fraud_detector.py \
        --data data/fraud_training.jsonl \
        --output scripts/output

    # In Colab: upload this file and run cells, or:
    #   !pip install torch transformers datasets onnx onnxruntime tf-nightly
    #   !python train_fraud_detector.py --data fraud_training.jsonl

Requirements:
    pip install torch transformers datasets scikit-learn onnx onnxruntime tqdm
    pip install tensorflow  # for TFLite conversion

Output:
    scripts/output/
    ├── fraud_detector.onnx
    ├── fraud_detector.tflite      (INT8 quantized, < 30MB)
    ├── fraud_vocab.txt            (WordPiece vocabulary)
    ├── label_map.json             (label ↔ index mapping)
    └── training_metrics.json      (accuracy, precision, recall, F1)
"""

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np

# ─── Label Configuration ──────────────────────────────────────────────────────

LABEL_LIST = [
    "safe",
    "investment",
    "romance",
    "impersonation",
    "shopping",
    "job",
    "loan",
    "lottery",
    "phishing",
]

LABEL2ID = {label: i for i, label in enumerate(LABEL_LIST)}
ID2LABEL = {i: label for i, label in enumerate(LABEL_LIST)}
NUM_LABELS = len(LABEL_LIST)


# ─── Data Loading ─────────────────────────────────────────────────────────────

def load_jsonl(path: str) -> list[dict]:
    """Load JSONL training data."""
    data = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            record = json.loads(line)
            text = record.get("text", "")
            label = record.get("label", "safe")
            if text and label in LABEL2ID:
                data.append({"text": text, "label": label})
    print(f"Loaded {len(data)} records from {path}")
    return data


def train_val_split(data: list[dict], val_ratio: float = 0.2, seed: int = 42):
    """Split data into train and validation sets (stratified)."""
    from collections import defaultdict
    import random

    random.seed(seed)
    by_label = defaultdict(list)
    for item in data:
        by_label[item["label"]].append(item)

    train_data, val_data = [], []
    for label, items in by_label.items():
        random.shuffle(items)
        split_idx = max(1, int(len(items) * (1 - val_ratio)))
        train_data.extend(items[:split_idx])
        val_data.extend(items[split_idx:])

    random.shuffle(train_data)
    random.shuffle(val_data)

    print(f"Train: {len(train_data)}, Val: {len(val_data)}")
    return train_data, val_data


# ─── Dataset ──────────────────────────────────────────────────────────────────

def create_dataset(data: list[dict], tokenizer, max_length: int = 256):
    """Create a PyTorch dataset from data list."""
    import torch
    from torch.utils.data import Dataset

    class FraudDataset(Dataset):
        def __init__(self, records, tok, max_len):
            self.records = records
            self.tok = tok
            self.max_len = max_len

        def __len__(self):
            return len(self.records)

        def __getitem__(self, idx):
            record = self.records[idx]
            encoding = self.tok(
                record["text"],
                truncation=True,
                padding="max_length",
                max_length=self.max_len,
                return_tensors="pt",
            )
            return {
                "input_ids": encoding["input_ids"].squeeze(0),
                "attention_mask": encoding["attention_mask"].squeeze(0),
                "labels": torch.tensor(LABEL2ID[record["label"]], dtype=torch.long),
            }

    return FraudDataset(data, tokenizer, max_length)


# ─── Training ─────────────────────────────────────────────────────────────────

def train_model(train_data, val_data, output_dir: str, epochs: int = 4, batch_size: int = 16, lr: float = 2e-5, max_length: int = 256):
    """Fine-tune bert-base-chinese for fraud classification."""
    import torch
    from torch.utils.data import DataLoader
    from transformers import BertTokenizer, BertForSequenceClassification, get_linear_schedule_with_warmup
    from sklearn.metrics import accuracy_score, precision_recall_fscore_support, classification_report

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Device: {device}")

    # Load tokenizer and model
    model_name = "bert-base-chinese"
    print(f"Loading {model_name}...")
    tokenizer = BertTokenizer.from_pretrained(model_name)
    model = BertForSequenceClassification.from_pretrained(
        model_name,
        num_labels=NUM_LABELS,
        id2label=ID2LABEL,
        label2id=LABEL2ID,
    ).to(device)

    # Create datasets
    train_dataset = create_dataset(train_data, tokenizer, max_length)
    val_dataset = create_dataset(val_data, tokenizer, max_length)

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    # Optimizer + scheduler
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=0.01)
    total_steps = len(train_loader) * epochs
    scheduler = get_linear_schedule_with_warmup(
        optimizer,
        num_warmup_steps=int(total_steps * 0.1),
        num_training_steps=total_steps,
    )

    # Training loop
    print(f"\nTraining for {epochs} epochs...")
    best_f1 = 0.0

    for epoch in range(epochs):
        # ── Train ──
        model.train()
        total_loss = 0
        for batch in train_loader:
            optimizer.zero_grad()
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["labels"].to(device)

            outputs = model(input_ids=input_ids, attention_mask=attention_mask, labels=labels)
            loss = outputs.loss
            loss.backward()

            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()

            total_loss += loss.item()

        avg_loss = total_loss / len(train_loader)

        # ── Validate ──
        model.eval()
        all_preds, all_labels = [], []
        with torch.no_grad():
            for batch in val_loader:
                input_ids = batch["input_ids"].to(device)
                attention_mask = batch["attention_mask"].to(device)
                labels = batch["labels"].to(device)

                outputs = model(input_ids=input_ids, attention_mask=attention_mask)
                preds = torch.argmax(outputs.logits, dim=-1)
                all_preds.extend(preds.cpu().numpy())
                all_labels.extend(labels.cpu().numpy())

        acc = accuracy_score(all_labels, all_preds)
        precision, recall, f1, _ = precision_recall_fscore_support(
            all_labels, all_preds, average="weighted", zero_division=0
        )

        print(f"  Epoch {epoch+1}/{epochs} — loss: {avg_loss:.4f}, "
              f"acc: {acc:.4f}, P: {precision:.4f}, R: {recall:.4f}, F1: {f1:.4f}")

        if f1 > best_f1:
            best_f1 = f1
            model.save_pretrained(os.path.join(output_dir, "best_model"))
            tokenizer.save_pretrained(os.path.join(output_dir, "best_model"))

    # Final evaluation with detailed report
    print("\n" + "=" * 60)
    print("Final Evaluation (best model)")
    print("=" * 60)

    # Reload best model
    model = BertForSequenceClassification.from_pretrained(
        os.path.join(output_dir, "best_model")
    ).to(device)
    model.eval()

    all_preds, all_labels = [], []
    with torch.no_grad():
        for batch in val_loader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["labels"].to(device)

            outputs = model(input_ids=input_ids, attention_mask=attention_mask)
            preds = torch.argmax(outputs.logits, dim=-1)
            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.cpu().numpy())

    acc = accuracy_score(all_labels, all_preds)
    precision, recall, f1, _ = precision_recall_fscore_support(
        all_labels, all_preds, average="weighted", zero_division=0
    )

    print(f"\nAccuracy:  {acc:.4f}")
    print(f"Precision: {precision:.4f}")
    print(f"Recall:    {recall:.4f}")
    print(f"F1 Score:  {f1:.4f}")
    print()
    print(classification_report(
        all_labels, all_preds,
        target_names=LABEL_LIST,
        zero_division=0,
    ))

    # Save metrics
    metrics = {
        "accuracy": round(acc, 4),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "epochs": epochs,
        "batch_size": batch_size,
        "learning_rate": lr,
        "max_length": max_length,
        "num_labels": NUM_LABELS,
        "label_list": LABEL_LIST,
        "train_size": len(train_data),
        "val_size": len(val_data),
    }
    metrics_path = os.path.join(output_dir, "training_metrics.json")
    with open(metrics_path, "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)
    print(f"Metrics saved to {metrics_path}")

    return model, tokenizer, metrics


# ─── Export: PyTorch → ONNX ───────────────────────────────────────────────────

def export_to_onnx(model, tokenizer, output_dir: str, max_length: int = 256):
    """Export PyTorch model to ONNX format."""
    import torch

    print("\nExporting to ONNX...")
    model.eval()
    model.cpu()

    onnx_path = os.path.join(output_dir, "fraud_detector.onnx")

    dummy_input = tokenizer(
        "這是一個測試句子",
        return_tensors="pt",
        padding="max_length",
        truncation=True,
        max_length=max_length,
    )

    torch.onnx.export(
        model,
        (dummy_input["input_ids"], dummy_input["attention_mask"]),
        onnx_path,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch_size"},
            "attention_mask": {0: "batch_size"},
            "logits": {0: "batch_size"},
        },
        opset_version=14,
    )

    size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
    print(f"  ONNX model saved: {onnx_path} ({size_mb:.1f} MB)")
    return onnx_path


# ─── Export: ONNX → TFLite (INT8) ─────────────────────────────────────────────

def export_to_tflite(onnx_path: str, output_dir: str, max_length: int = 256):
    """Convert ONNX model to TFLite with INT8 quantization."""
    print("\nConverting ONNX → TFLite (INT8)...")

    try:
        import onnxruntime as ort
        import tensorflow as tf
    except ImportError as e:
        print(f"  ERROR: Missing dependency for TFLite conversion: {e}")
        print("  Install: pip install onnxruntime tensorflow")
        return None

    # Step 1: Run ONNX inference to get representative data shape
    session = ort.InferenceSession(onnx_path)

    # Step 2: Create a simple TFLite-compatible model wrapper
    # We use tf.function to trace the computation
    input_ids_shape = [1, max_length]
    attention_mask_shape = [1, max_length]

    class OnnxWrapper(tf.Module):
        def __init__(self, session):
            super().__init__()
            self.session = session

        @tf.function(input_signature=[
            tf.TensorSpec(shape=input_ids_shape, dtype=tf.int32, name="input_ids"),
            tf.TensorSpec(shape=attention_mask_shape, dtype=tf.int32, name="attention_mask"),
        ])
        def predict(self, input_ids, attention_mask):
            # This is a placeholder — actual conversion uses ONNX → SavedModel → TFLite
            return input_ids  # placeholder

    # Alternative approach: ONNX → SavedModel via onnx-tf, then SavedModel → TFLite
    try:
        from onnx_tf.backend import prepare
        import onnx

        print("  Loading ONNX model...")
        onnx_model = onnx.load(onnx_path)

        print("  Converting to TensorFlow SavedModel...")
        saved_model_dir = os.path.join(output_dir, "saved_model")
        tf_rep = prepare(onnx_model)
        tf_rep.export_graph(saved_model_dir)

        print("  Converting to TFLite with INT8 quantization...")
        converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.int8]

        # Representative dataset for calibration
        def representative_dataset():
            for _ in range(100):
                input_ids = np.random.randint(0, 21128, size=input_ids_shape).astype(np.int32)
                attention_mask = np.ones(attention_mask_shape, dtype=np.int32)
                yield [input_ids, attention_mask]

        converter.representative_dataset = representative_dataset
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
            tf.lite.OpsSet.TFLITE_BUILTINS,
        ]

        tflite_model = converter.convert()

        tflite_path = os.path.join(output_dir, "fraud_detector.tflite")
        with open(tflite_path, "wb") as f:
            f.write(tflite_model)

        size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
        print(f"  TFLite model saved: {tflite_path} ({size_mb:.1f} MB)")

        if size_mb > 30:
            print(f"  WARNING: Model size ({size_mb:.1f} MB) exceeds 30 MB target!")
            print("  Consider using more aggressive quantization or a smaller model.")

        return tflite_path

    except ImportError:
        print("  onnx-tf not available, using alternative conversion method...")
        print("  Install: pip install onnx-tf")

        # Fallback: direct TFLite conversion using onnxruntime + quantization
        try:
            from onnxruntime.quantization import quantize_dynamic, QuantType

            quantized_onnx = os.path.join(output_dir, "fraud_detector_quantized.onnx")
            quantize_dynamic(
                onnx_path,
                quantized_onnx,
                weight_type=QuantType.QInt8,
            )
            size_mb = os.path.getsize(quantized_onnx) / (1024 * 1024)
            print(f"  Quantized ONNX saved: {quantized_onnx} ({size_mb:.1f} MB)")
            print("  NOTE: Full TFLite conversion requires onnx-tf. Install it and re-run.")
            print("  For now, the quantized ONNX can be converted manually.")
            return quantized_onnx
        except Exception as e2:
            print(f"  Fallback quantization also failed: {e2}")
            return None


# ─── Export Vocabulary ─────────────────────────────────────────────────────────

def export_vocab(tokenizer, output_dir: str):
    """Export WordPiece vocabulary for Android tokenizer."""
    vocab_path = os.path.join(output_dir, "fraud_vocab.txt")

    # bert-base-chinese uses vocab.txt
    vocab = tokenizer.get_vocab()
    sorted_vocab = sorted(vocab.items(), key=lambda x: x[1])

    with open(vocab_path, "w", encoding="utf-8") as f:
        for token, _ in sorted_vocab:
            f.write(token + "\n")

    print(f"Vocabulary saved: {vocab_path} ({len(sorted_vocab)} tokens)")
    return vocab_path


def export_label_map(output_dir: str):
    """Export label mapping for Android inference."""
    label_map = {
        "label2id": LABEL2ID,
        "id2label": ID2LABEL,
        "label_list": LABEL_LIST,
    }
    label_path = os.path.join(output_dir, "label_map.json")
    with open(label_path, "w", encoding="utf-8") as f:
        json.dump(label_map, f, ensure_ascii=False, indent=2)
    print(f"Label map saved: {label_path}")


# ─── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Train fraud detector model (bert-base-chinese → TFLite)"
    )
    parser.add_argument(
        "--data", "-d",
        default="data/fraud_training.jsonl",
        help="Input JSONL training data (default: data/fraud_training.jsonl)",
    )
    parser.add_argument(
        "--output", "-o",
        default="scripts/output",
        help="Output directory (default: scripts/output)",
    )
    parser.add_argument("--epochs", type=int, default=4, help="Training epochs (default: 4)")
    parser.add_argument("--batch-size", type=int, default=16, help="Batch size (default: 16)")
    parser.add_argument("--lr", type=float, default=2e-5, help="Learning rate (default: 2e-5)")
    parser.add_argument("--max-length", type=int, default=256, help="Max token length (default: 256)")
    parser.add_argument("--skip-tflite", action="store_true", help="Skip TFLite conversion")

    args = parser.parse_args()

    os.makedirs(args.output, exist_ok=True)

    print("=" * 60)
    print("FraudAI Model Training")
    print("=" * 60)
    print(f"  Data:       {args.data}")
    print(f"  Output:     {args.output}")
    print(f"  Epochs:     {args.epochs}")
    print(f"  Batch size: {args.batch_size}")
    print(f"  LR:         {args.lr}")
    print(f"  Max length: {args.max_length}")
    print(f"  Labels:     {NUM_LABELS} ({', '.join(LABEL_LIST)})")
    print()

    # 1. Load data
    data = load_jsonl(args.data)
    if len(data) < 10:
        print("ERROR: Not enough training data (need at least 10 records).")
        sys.exit(1)

    train_data, val_data = train_val_split(data)

    # 2. Train
    model, tokenizer, metrics = train_model(
        train_data, val_data,
        output_dir=args.output,
        epochs=args.epochs,
        batch_size=args.batch_size,
        lr=args.lr,
        max_length=args.max_length,
    )

    # 3. Export vocab + label map
    export_vocab(tokenizer, args.output)
    export_label_map(args.output)

    # 4. Export to ONNX
    onnx_path = export_to_onnx(model, tokenizer, args.output, args.max_length)

    # 5. Export to TFLite
    if not args.skip_tflite:
        tflite_path = export_to_tflite(onnx_path, args.output, args.max_length)
        if tflite_path:
            print(f"\n  Final TFLite model: {tflite_path}")

    print("\n" + "=" * 60)
    print("Training complete!")
    print("=" * 60)
    print(f"  Accuracy:  {metrics['accuracy']}")
    print(f"  Precision: {metrics['precision']}")
    print(f"  Recall:    {metrics['recall']}")
    print(f"  F1 Score:  {metrics['f1']}")
    print()
    print("Next steps:")
    print("  1. Copy fraud_detector.tflite to app/src/main/assets/")
    print("  2. Copy fraud_vocab.txt to app/src/main/assets/")
    print("  3. Build and test on device")


if __name__ == "__main__":
    main()
