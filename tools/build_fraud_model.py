#!/usr/bin/env python3
"""
YukiBoard 防詐偵測 TFLite 模型產生器 v1。

模型規格（對應 FraudLocalModel.kt）：
  Input 0: input_ids     int32[1, 256]
  Input 1: attention_mask int32[1, 256]
  Output 0: logits       float32[1, 9]

9 類標籤：safe, investment, romance, impersonation, shopping, job, loan, lottery, phishing

使用 flatbuffers + tflite schema 直接產生 .tflite 檔。
"""

import numpy as np
import os
import struct
import flatbuffers

# TFLite schema constants
class TensorType:
    FLOAT32 = 0
    INT32 = 2

class BuiltinOperator:
    FULLY_CONNECTED = 9
    RESHAPE = 22
    MEAN = 25
    MUL = 18
    GATHER = 52
    CAST = 53
    ADD = 0
    SUM = 90
    DIV = 64
    EXPAND_DIMS = 70

class ActivationFunctionType:
    NONE = 0
    RELU = 1

class Padding:
    SAME = 0
    VALID = 1

# ═══════════════════════════════════════════════════════════
# 1. 詞表
# ═══════════════════════════════════════════════════════════

SPECIAL_TOKENS = ["[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]"]

FRAUD_KEYWORDS_ZH = [
    # 投資詐騙 (0-32)
    "投資", "理財", "獲利", "報酬", "穩賺", "保本", "基金", "期貨", "虛擬幣",
    "比特幣", "以太幣", "區塊鏈", "挖礦", "收益", "翻倍", "飆漲", "股票",
    "配息", "年化", "本金", "入金", "出金", "操盤", "帶單", "跟單",
    "平台", "交易所", "老師", "分析師", "群組", "加入", "私訊", "高回報",
    # 感情詐騙 (33-57)
    "寶貝", "親愛的", "想你", "愛你", "結婚", "見面", "照片", "視頻",
    "軍人", "醫生", "工程師", "海外", "香港", "國外", "寂寞", "孤單",
    "禮物", "紅包", "生日", "急需", "幫忙", "借", "匯款", "感情", "交往",
    # 冒充詐騙 (58-79)
    "警察", "檢察官", "法院", "銀行", "客服", "帳戶", "凍結", "異常",
    "洗錢", "涉案", "逮捕", "傳票", "配合", "調查", "監管",
    "安全帳戶", "公證", "提款", "轉帳", "ATM", "操作", "身份",
    # 購物詐騙 (80-103)
    "免費", "優惠", "折扣", "搶購", "限量", "特價", "代購", "便宜",
    "貨到付款", "運費", "退款", "賣家", "買家", "面交", "蝦皮", "拍賣",
    "商品", "訂單", "出貨", "物流", "快遞", "追蹤", "包裹", "付款",
    # 求職詐騙 (104-120)
    "日薪", "高薪", "兼職", "在家", "工作", "招聘", "月入", "輕鬆",
    "門檻", "不需經驗", "時薪", "薪水", "佣金", "提成", "人頭",
    "銀行卡", "保證金",
    # 貸款詐騙 (121-135)
    "貸款", "借款", "利率", "低息", "信用", "額度", "審核", "放款",
    "手續費", "代辦", "快速", "當天", "撥款", "擔保", "抵押",
    # 中獎詐騙 (136-148)
    "中獎", "恭喜", "得獎", "抽獎", "獎金", "稅金", "手續", "領獎",
    "彩券", "刮刮樂", "幸運", "被選中", "頭獎",
    # 釣魚詐騙 (149-163)
    "驗證", "密碼", "登入", "確認", "連結", "點擊", "更新", "過期",
    "帳號", "資料", "信用卡", "安全碼", "OTP", "簡訊", "網址",
    # 通用風險詞
    "錢", "匯", "轉", "急", "趕快", "馬上", "立刻", "今天",
    "不要告訴", "保密", "機會", "錯過", "限時", "名額", "最後",
]

FRAUD_KEYWORDS_EN = [
    "invest", "profit", "return", "guaranteed", "bitcoin", "crypto",
    "ethereum", "blockchain", "mining", "yield", "trading", "platform",
    "broker", "forex", "stock", "dividend",
    "love", "darling", "miss", "marry", "meet", "photo", "video",
    "soldier", "doctor", "engineer", "overseas", "lonely", "gift",
    "police", "prosecutor", "court", "bank", "account", "frozen",
    "suspicious", "arrest", "warrant", "investigate", "transfer",
    "free", "discount", "limited", "cheap", "refund", "seller",
    "buyer", "order", "shipping", "delivery", "package", "tracking",
    "salary", "job", "hiring", "easy", "commission", "deposit",
    "loan", "borrow", "interest", "credit", "approval", "fee",
    "winner", "congratulations", "prize", "lottery", "lucky", "claim",
    "verify", "password", "login", "confirm", "click", "link",
    "update", "expired", "security", "code", "otp", "sms",
    "money", "urgent", "hurry", "secret", "opportunity", "act", "now",
    "send", "wire",
]

COMMON_CHARS = list("abcdefghijklmnopqrstuvwxyz0123456789")
SUBWORD_PREFIXES = ["##" + c for c in "abcdefghijklmnopqrstuvwxyz"]
COMMON_ZH = list("的一是不了人我在有他這中大來上個國到說時要就出會也你對生能都")
COMMON_ZH += list("而子那得於著下自之年過發後作里用道行所然家種事成方多經麼好")
PUNCTUATION = list("，。！？、；：「」（）—…,.!?;:()[]{}\"'-/\\@#$%^&*")


def build_vocab():
    tokens = []
    tokens.extend(SPECIAL_TOKENS)
    tokens.extend(COMMON_CHARS)
    tokens.extend(SUBWORD_PREFIXES)
    tokens.extend(PUNCTUATION)
    tokens.extend(COMMON_ZH)
    tokens.extend(FRAUD_KEYWORDS_ZH)
    tokens.extend(FRAUD_KEYWORDS_EN)
    seen = set()
    unique = []
    for t in tokens:
        if t not in seen:
            seen.add(t)
            unique.append(t)
    return unique


# ═══════════════════════════════════════════════════════════
# 2. 用 tflite schema 建 flatbuffer
# ═══════════════════════════════════════════════════════════

import tflite

MAX_SEQ = 256
EMBED_DIM = 32
HIDDEN_DIM = 64
NUM_LABELS = 9


def create_keyword_weights(vocab_tokens):
    np.random.seed(42)
    vocab_size = len(vocab_tokens)
    token_to_id = {t: i for i, t in enumerate(vocab_tokens)}

    # Category keyword index ranges (approximate based on list above)
    categories = {
        1: (0, 33),    # investment
        2: (33, 58),   # romance
        3: (58, 80),   # impersonation
        4: (80, 104),  # shopping
        5: (104, 121), # job
        6: (121, 136), # loan
        7: (136, 149), # lottery
        8: (149, 164), # phishing
    }

    # Embedding: vocab_size x EMBED_DIM
    embedding = np.random.randn(vocab_size, EMBED_DIM).astype(np.float32) * 0.02
    embedding[0] = 0  # [PAD]

    # Build the ZH keyword offset
    zh_start_idx = len(SPECIAL_TOKENS) + len(COMMON_CHARS) + len(SUBWORD_PREFIXES)
    # After punctuation and common ZH chars
    punct_unique = []
    seen = set(SPECIAL_TOKENS + COMMON_CHARS + SUBWORD_PREFIXES)
    for p in PUNCTUATION:
        if p not in seen:
            punct_unique.append(p)
            seen.add(p)
    common_zh_unique = []
    for c in COMMON_ZH:
        if c not in seen:
            common_zh_unique.append(c)
            seen.add(c)

    zh_kw_offset = zh_start_idx + len(punct_unique) + len(common_zh_unique)

    # For each category, set strong embedding directions
    for cat_id, (start, end) in categories.items():
        # Category gets specific dims as its "signature"
        base_dim = (cat_id * 4) % EMBED_DIM
        for i, kw in enumerate(FRAUD_KEYWORDS_ZH[start:end]):
            if kw in token_to_id:
                tid = token_to_id[kw]
                for d_off in range(4):
                    d = (base_dim + d_off) % EMBED_DIM
                    embedding[tid][d] = 0.8 + np.random.rand() * 0.2

    # English keywords
    en_cat_ranges = {
        1: (0, 16),
        2: (16, 29),
        3: (29, 40),
        4: (40, 52),
        5: (52, 58),
        6: (58, 64),
        7: (64, 70),
        8: (70, 82),
    }
    for cat_id, (start, end) in en_cat_ranges.items():
        base_dim = (cat_id * 4) % EMBED_DIM
        for kw in FRAUD_KEYWORDS_EN[start:end]:
            if kw in token_to_id:
                tid = token_to_id[kw]
                for d_off in range(4):
                    d = (base_dim + d_off) % EMBED_DIM
                    embedding[tid][d] = 0.7 + np.random.rand() * 0.3

    # W1: EMBED_DIM → HIDDEN_DIM
    W1 = np.random.randn(EMBED_DIM, HIDDEN_DIM).astype(np.float32) * 0.1
    b1 = np.zeros(HIDDEN_DIM, dtype=np.float32)

    # W2: HIDDEN_DIM → NUM_LABELS
    W2 = np.random.randn(HIDDEN_DIM, NUM_LABELS).astype(np.float32) * 0.1
    b2 = np.zeros(NUM_LABELS, dtype=np.float32)
    b2[0] = 2.0  # Bias towards "safe"

    return embedding, W1, b1, W2, b2


def build_tflite_model(vocab_tokens):
    """
    Build TFLite model using flatbuffers.

    Graph:
      0: input_ids [1,256] int32         (input)
      1: attention_mask [1,256] int32     (input)
      → GATHER(embedding, input_ids) → [1,256,32]
      → CAST(attention_mask) → [1,256] float
      → RESHAPE → [1,256,1]
      → MUL(gathered, mask_reshaped) → [1,256,32]
      → SUM(axis=1) → [1,32]    (simplified: MEAN instead)
      → FULLY_CONNECTED(W1,b1,RELU) → [1,64]
      → FULLY_CONNECTED(W2,b2,NONE) → [1,9]
      → output [1,9] float32             (output)
    """
    vocab_size = len(vocab_tokens)
    embedding, W1, b1, W2, b2 = create_keyword_weights(vocab_tokens)

    B = flatbuffers.Builder(2 * 1024 * 1024)

    # ---- Build buffers ----
    # Buffer 0: empty (required)
    # Buffer 1: embedding [vocab_size, 32] float32
    # Buffer 2: W1 [32, 64] float32 → transposed to [64, 32] for FC
    # Buffer 3: b1 [64] float32
    # Buffer 4: W2 [64, 9] float32 → transposed to [9, 64] for FC
    # Buffer 5: b2 [9] float32
    # Buffer 6: reshape shape [1, 256, 1] int32
    # Buffer 7: mean axis [1] int32 → value 1
    # Buffer 8-13: empty (intermediate tensors)

    # For FULLY_CONNECTED, weights must be [output_size, input_size]
    W1_t = W1.T.copy().astype(np.float32)  # [64, 32]
    W2_t = W2.T.copy().astype(np.float32)  # [9, 64]
    reshape_shape = np.array([1, MAX_SEQ, 1], dtype=np.int32)
    mean_axis = np.array([1], dtype=np.int32)

    buffer_data = [
        None,                       # 0: empty
        embedding.tobytes(),        # 1: embedding
        W1_t.tobytes(),             # 2: W1 transposed
        b1.tobytes(),               # 3: b1
        W2_t.tobytes(),             # 4: W2 transposed
        b2.tobytes(),               # 5: b2
        reshape_shape.tobytes(),    # 6: reshape shape
        mean_axis.tobytes(),        # 7: mean axis
        None, None, None, None,     # 8-11: intermediates
        None, None,                 # 12-13
    ]

    # Create buffer objects
    buf_offsets = []
    for bd in buffer_data:
        if bd is not None:
            data_vec = B.CreateByteVector(bd)
        else:
            data_vec = None

        tflite.BufferStart(B)
        if data_vec is not None:
            tflite.BufferAddData(B, data_vec)
        buf_offsets.append(tflite.BufferEnd(B))

    # Buffers vector
    tflite.ModelStartBuffersVector(B, len(buf_offsets))
    for bo in reversed(buf_offsets):
        B.PrependUOffsetTRelative(bo)
    buffers_vec = B.EndVector()

    # ---- Build tensors ----
    # Tensor: name, shape, type, buffer_idx
    tensor_specs = [
        # idx, name, shape, type, buffer
        (0,  "input_ids",       [1, MAX_SEQ],            TensorType.INT32,   0),
        (1,  "attention_mask",  [1, MAX_SEQ],            TensorType.INT32,   0),
        (2,  "embedding_w",    [vocab_size, EMBED_DIM],  TensorType.FLOAT32, 1),
        (3,  "gathered",       [1, MAX_SEQ, EMBED_DIM],  TensorType.FLOAT32, 8),
        (4,  "mask_float",     [1, MAX_SEQ],             TensorType.FLOAT32, 9),
        (5,  "reshape_shape",  [3],                      TensorType.INT32,   6),
        (6,  "mask_3d",        [1, MAX_SEQ, 1],          TensorType.FLOAT32, 10),
        (7,  "masked",         [1, MAX_SEQ, EMBED_DIM],  TensorType.FLOAT32, 11),
        (8,  "mean_axis",      [1],                      TensorType.INT32,   7),
        (9,  "pooled",         [1, EMBED_DIM],           TensorType.FLOAT32, 12),
        (10, "W1",             [HIDDEN_DIM, EMBED_DIM],  TensorType.FLOAT32, 2),
        (11, "b1",             [HIDDEN_DIM],             TensorType.FLOAT32, 3),
        (12, "hidden",         [1, HIDDEN_DIM],          TensorType.FLOAT32, 13),
        (13, "W2",             [NUM_LABELS, HIDDEN_DIM], TensorType.FLOAT32, 4),
        (14, "b2",             [NUM_LABELS],             TensorType.FLOAT32, 5),
        (15, "output",         [1, NUM_LABELS],          TensorType.FLOAT32, 0),
    ]

    tensor_offsets = []
    for idx, name, shape, dtype, buf_idx in tensor_specs:
        name_off = B.CreateString(name)
        tflite.TensorStartShapeVector(B, len(shape))
        for s in reversed(shape):
            B.PrependInt32(s)
        shape_vec = B.EndVector()

        tflite.TensorStart(B)
        tflite.TensorAddName(B, name_off)
        tflite.TensorAddShape(B, shape_vec)
        tflite.TensorAddType(B, dtype)
        tflite.TensorAddBuffer(B, buf_idx)
        tensor_offsets.append(tflite.TensorEnd(B))

    # Tensors vector
    tflite.SubGraphStartTensorsVector(B, len(tensor_offsets))
    for to in reversed(tensor_offsets):
        B.PrependUOffsetTRelative(to)
    tensors_vec = B.EndVector()

    # ---- Build operators ----
    # Op 0: GATHER(embedding_w=2, input_ids=0) → gathered=3
    # Op 1: CAST(attention_mask=1) → mask_float=4
    # Op 2: RESHAPE(mask_float=4, reshape_shape=5) → mask_3d=6
    # Op 3: MUL(gathered=3, mask_3d=6) → masked=7
    # Op 4: MEAN(masked=7, mean_axis=8) → pooled=9
    # Op 5: FULLY_CONNECTED(pooled=9, W1=10, b1=11) RELU → hidden=12
    # Op 6: FULLY_CONNECTED(hidden=12, W2=13, b2=14) NONE → output=15

    ops_info = [
        # (opcode_index, inputs, outputs, builtin_options_type, options_builder)
    ]

    # Operator codes
    opcodes_list = [
        BuiltinOperator.GATHER,           # 0
        BuiltinOperator.CAST,             # 1
        BuiltinOperator.RESHAPE,          # 2
        BuiltinOperator.MUL,              # 3
        BuiltinOperator.MEAN,             # 4
        BuiltinOperator.FULLY_CONNECTED,  # 5
    ]

    opcode_offsets = []
    for op_code in opcodes_list:
        tflite.OperatorCodeStart(B)
        tflite.OperatorCodeAddDeprecatedBuiltinCode(B, min(op_code, 127))
        tflite.OperatorCodeAddBuiltinCode(B, op_code)
        opcode_offsets.append(tflite.OperatorCodeEnd(B))

    tflite.ModelStartOperatorCodesVector(B, len(opcode_offsets))
    for oc in reversed(opcode_offsets):
        B.PrependUOffsetTRelative(oc)
    opcodes_vec = B.EndVector()

    # Build FC options (for RELU activation)
    tflite.FullyConnectedOptionsStart(B)
    tflite.FullyConnectedOptionsAddFusedActivationFunction(B, ActivationFunctionType.RELU)
    fc_relu_opts = tflite.FullyConnectedOptionsEnd(B)

    tflite.FullyConnectedOptionsStart(B)
    tflite.FullyConnectedOptionsAddFusedActivationFunction(B, ActivationFunctionType.NONE)
    fc_none_opts = tflite.FullyConnectedOptionsEnd(B)

    # Build each operator
    def make_op(opcode_idx, inputs, outputs, builtin_opts_type=0, builtin_opts=None):
        tflite.OperatorStartInputsVector(B, len(inputs))
        for inp in reversed(inputs):
            B.PrependInt32(inp)
        inputs_vec = B.EndVector()

        tflite.OperatorStartOutputsVector(B, len(outputs))
        for out in reversed(outputs):
            B.PrependInt32(out)
        outputs_vec = B.EndVector()

        tflite.OperatorStart(B)
        tflite.OperatorAddOpcodeIndex(B, opcode_idx)
        tflite.OperatorAddInputs(B, inputs_vec)
        tflite.OperatorAddOutputs(B, outputs_vec)
        if builtin_opts is not None:
            tflite.OperatorAddBuiltinOptionsType(B, builtin_opts_type)
            tflite.OperatorAddBuiltinOptions(B, builtin_opts)
        return tflite.OperatorEnd(B)

    # BuiltinOptions type for FullyConnected = 9
    FC_OPTIONS_TYPE = 9

    op_offsets = [
        make_op(0, [2, 0], [3]),                                    # GATHER
        make_op(1, [1], [4]),                                       # CAST
        make_op(2, [4, 5], [6]),                                    # RESHAPE
        make_op(3, [3, 6], [7]),                                    # MUL
        make_op(4, [7, 8], [9]),                                    # MEAN
        make_op(5, [9, 10, 11], [12], FC_OPTIONS_TYPE, fc_relu_opts),  # FC+RELU
        make_op(5, [12, 13, 14], [15], FC_OPTIONS_TYPE, fc_none_opts), # FC
    ]

    tflite.SubGraphStartOperatorsVector(B, len(op_offsets))
    for op in reversed(op_offsets):
        B.PrependUOffsetTRelative(op)
    ops_vec = B.EndVector()

    # ---- Build subgraph ----
    subgraph_name = B.CreateString("main")

    tflite.SubGraphStartInputsVector(B, 2)
    B.PrependInt32(1)  # attention_mask
    B.PrependInt32(0)  # input_ids
    inputs_vec = B.EndVector()

    tflite.SubGraphStartOutputsVector(B, 1)
    B.PrependInt32(15)  # output
    outputs_vec = B.EndVector()

    tflite.SubGraphStart(B)
    tflite.SubGraphAddName(B, subgraph_name)
    tflite.SubGraphAddTensors(B, tensors_vec)
    tflite.SubGraphAddInputs(B, inputs_vec)
    tflite.SubGraphAddOutputs(B, outputs_vec)
    tflite.SubGraphAddOperators(B, ops_vec)
    subgraph = tflite.SubGraphEnd(B)

    tflite.ModelStartSubgraphsVector(B, 1)
    B.PrependUOffsetTRelative(subgraph)
    subgraphs_vec = B.EndVector()

    # ---- Build description ----
    desc = B.CreateString("YukiBoard Fraud Detector v1")

    # ---- Build model ----
    tflite.ModelStart(B)
    tflite.ModelAddVersion(B, 3)
    tflite.ModelAddDescription(B, desc)
    tflite.ModelAddOperatorCodes(B, opcodes_vec)
    tflite.ModelAddSubgraphs(B, subgraphs_vec)
    tflite.ModelAddBuffers(B, buffers_vec)
    model = tflite.ModelEnd(B)

    B.Finish(model, b"TFL3")

    return bytes(B.Output())


# ═══════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════

if __name__ == "__main__":
    output_dir = os.path.dirname(os.path.abspath(__file__))

    # 1. Build vocab
    vocab_tokens = build_vocab()
    print(f"Vocab size: {len(vocab_tokens)}")

    vocab_path = os.path.join(output_dir, "fraud_vocab.txt")
    with open(vocab_path, "w", encoding="utf-8") as f:
        for token in vocab_tokens:
            f.write(token + "\n")
    print(f"Vocab: {vocab_path}")

    # 2. Build TFLite model
    tflite_bytes = build_tflite_model(vocab_tokens)
    model_path = os.path.join(output_dir, "fraud_detector.tflite")
    with open(model_path, "wb") as f:
        f.write(tflite_bytes)
    print(f"Model: {model_path} ({len(tflite_bytes)} bytes, {len(tflite_bytes)/1024:.1f} KB)")

    # 3. Also copy to assets
    assets_dir = os.path.join(output_dir, "..", "app", "src", "main", "assets")
    if os.path.isdir(assets_dir):
        import shutil
        shutil.copy2(model_path, os.path.join(assets_dir, "fraud_detector.tflite"))
        shutil.copy2(vocab_path, os.path.join(assets_dir, "fraud_vocab.txt"))
        print(f"Copied to assets/")
    else:
        print(f"Assets dir not found: {assets_dir}")

    print("Done!")
