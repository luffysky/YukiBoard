#!/usr/bin/env python3
"""
Expand YukiBoard english_words.txt from ~20,000 to 50,000 words.

This script:
1. Reads the existing english_words.txt
2. Generates additional common English words from multiple categories
3. Applies morphological expansion (prefixes/suffixes) to known roots
4. Writes back the expanded file, preserving existing order

Run: python scripts/expand_english.py
"""

import os
import sys
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
DICT_PATH = os.path.join(PROJECT_DIR, "app", "src", "main", "res", "raw", "english_words.txt")
TARGET_COUNT = 50000


ORIGINAL_COUNT = 20000  # The original dictionary had exactly 20,000 words


def load_existing():
    with open(DICT_PATH, "r", encoding="utf-8") as f:
        words = [line.strip().lower() for line in f if line.strip()]
    # If file was previously expanded with bad data, only keep originals
    if len(words) > ORIGINAL_COUNT:
        print(f"  File has {len(words)} words, truncating to original {ORIGINAL_COUNT}")
        words = words[:ORIGINAL_COUNT]
    return words


# ─── CATEGORY WORD LISTS ───────────────────────────────────────────────

TECHNOLOGY = """
algorithm algorithms api apis app apps bandwidth bitcoin blockchain bot bots
browser browsers bug bugs byte bytes cache caches chatbot chatbots cli cloud
codec codecs codebase compile compiler compilers container containers cookie
cookies cpu cpus cryptocurrency css cuda cybersecurity daemon daemons dashboard
dashboards dataset datasets debug debugger deployment devops dns docker domain
domains download downloads driver drivers dropdown dropdowns ecosystem emoji
emojis encrypt encryption endpoint endpoints ethernet favicon fiber filesystem
firewall firewalls firmware frontend fullstack fullscreen gateway gateways
gigabyte git github gitlab gpu gpus graphql hack hacker hackers hashtag
hashtags homepage html http https hyperlink hyperlinks icon icons ide ides
inbox inboxes infographic infographics infrastructure inline instagram integer
integers internet intranet ios iphone iot iterator iterators java javascript
jpeg json kernel kernels keyboard keyboards kilobyte kubernetes lan laptop
laptops latency linux localhost login logins logout logouts malware markup
megabyte metadata microservice microservices middleware modem modems motherboard
mysql namespace namespaces navbar navbars nginx node nodejs notebook notebooks
npm offline onclick online opcode opcodes opensource opensource optimization
optimize optimizer os overclock overflow overflows packet packets parameter
parameters parser parsers password passwords payload payloads permalink
permalinks phishing php pixel pixels platform platforms plugin plugins podcast
podcasts popup popups portal portals postgres postgresql preprocessor processors
program programmer programmers programming protocol protocols proxy proxies
python qr query queries queue queues ram ransomware react readme realtime
reboot refactor regex render renderer rendering repo repos repository responsive
restful robot robots router routers ruby runtime runtimes sandbox sass scala
scalability screenshot screenshots script scripting scripts sdk sdks selenium
semiconductor server servers servlet servlets sidebar sidebars signup signups
silicon smartphone smartphones smtp snippet snippets socket sockets spam sql
sqlite ssd ssh ssl startup startups stylesheet stylesheets subnet subnets sudo
svg swipe sync syntax sysadmin tab tabs tcp template templates terabyte
terminal terminals terraform textbox thread threads throughput thumbnail
thumbnails timestamp timestamps toggle toggles token tokens toolbar toolbars
tooltip tooltips touchpad touchscreen trackpad transistor transistors trojan
typescript ubuntu ui ux upload uploads url urls usb username usernames uuid
vanilla vcard vector vectors verilog vim vintage virtual virtualization virus
viruses vlc vm vms vpn vpns vulnerability vulnerabilities wasm webapp webapps
webcam webcams webhook webhooks webmaster webpage webpages webserver website
websites websocket websockets widget widgets wifi wiki wikis wildcard windows
wireless wordpress workflow workflows workstation workstations worm worms
wrapper wrappers wysiwyg xaml xml xpath yaml zip
ansible ansible apache apm async asynchronous aws azure backend backlog
bandwidth benchmark benchmarks binary binaries biometric biometrics bitmap
bitrate bluetooth boolean bootloader bootstrap broadband broadcast buffer
buffers bytecode callback callbacks captcha carousel carousels cdn cgi
changelog changelogs checkbox checkboxes chipset chipsets chromium cicd cipher
ciphers clipboard clipboards cluster clusters cobol codec codecs codepoint
collision collisions commandline committer config configs constraint constraints
constructor constructors cors cpanel cron crontab cronjob cursor cursors
cyberattack darkweb dataflow datagram datagrams datatype datatypes decryption
denial deprecated descriptor descriptors destructor destructors devtools
dialog dialogs directive directives disassemble dispatcher dispatchers
dockerfile docstring docstrings dongle dongles dotnet downtime drupal duplex
ecommerce elasticsearch endian enum enums epoch erlang escalation ethernet
eval event events exception exceptions executable executables exploit exploits
extension extensions extraction failover failovers fallback fallbacks fifo
fingerprint fingerprinting flask flatfile floating floppy flowchart flyout
flyouts footprint forensic forensics formatter formatters fragment fragments
framebuffer framework frameworks freeware fullstack gadget gadgets garbage
geocode geocoding getter getters gigabit gigabits glitch glitches gnome gnu
golang greylist greyhat gridview gzip hadoop handshake handshakes hardcode
hardcoded hashmap hashmaps heapdump hexadecimal hotfix hotfixes hotkey hotkeys
hotspot hotspots href hypervisor hypervisors identifier identifiers immutable
implement implementation implementations import imports increment increments
index indexes indexing inheritance inject injection injections inline inodes
instance instances instantiate integer integers integrate integration
integrations intercept interceptor interceptors interop interpolation interrupt
interrupts invoke invoker iptables iterate iterable iterables iteration
iterations iterator iterators jailbreak jailbreaks jenkins jira jit jvm jvms
kafka kerberos keychain keylogger keyloggers keypress keypresses keyring
keystore keystroke keystrokes kubernetes kubectl lambda lambdas ldap lexer
lexers libc lifecycle lifecycles linter linters linker linkers livepatch
loadbalancer localhost lockfile lockfiles lossless lossy lua lxc lxd macro
macros mainframe mainframes malloc manpage manpages marshalling memcache
memcached metaclass metadata microcontroller microcontrollers middleware
migration migrations minify minimap minimaps mirroring mockup mockups
modular module modules monolith monoliths mqtt multithreaded multithreading
mutex mutexes namespace namespaces netfilter netmask netstat nginx nmap node
nonce nonces nosql npm nuget nullable oauth obfuscate obfuscation objectcode
observable observables ocr offset offsets onpremise onpremises opcache opcode
opcodes opengl openssl operand operands optimizer optimizers orchestration
orchestrator orchestrators outage outages overclocking overflow overflows
overload overloads override overrides packer pagination paginations pager
pagers parallelize parallelism parameter parameters parity partition
partitions passphrase passphrases patchset payloads pcb pcbs peering
percentile percentiles permalink permalinks persistence phishing pingback
pipeline pipelines platformio playbook playbooks polyfill polyfills popup
popups postcss pragma pragmas precompile precompiled preemptive preloader
preprocessor preprocessors prioritize profiler profilers provisioning
provisioner pseudocode pseudorandom pullrequest pullrequests purge purges
pushdown pushnotification pwm querystring quirksmode racetrack radix
randomize randomizer ransom ransomware rasterize rasterizer readline readwrite
realtime rebase rebases recursion recursive redhat redis redirect redirects
redundancy refactoring regex regexes registry registries reinitialize
relational replication responder responders restful retransmit retransmits
retrofit reverseproxy revocation rollback rollbacks rootkit rootkits roundrobin
rsync runbook runbooks runtime runtimes saltstack sanitize sanitizer scheduler
schedulers schemaless scoping screenshot screenshots scrubbing segfault
segfaults semaphore semaphores serialization serialize serializer serverless
servlet servlets setter setters sha sharding shellcode shellcodes shim shims
signup signups singleton singletons sitemap sitemaps slider sliders smb smtp
snapshot snapshots sniffing sniffer sniffers socketio softcoded solaris solr
sourcemap sourcemaps spaghetti sparkline sparklines spawn spawner spinnaker
splunk spoofing sprintf sqlinjection squash squashes sre ssh sshkey ssl
stackoverflow staging stateful stateless stderr stdin stdout stochastic
stringbuilder stringbuffer stringsearch struct structs subprocess subquery
subqueries subroutine subroutines sudo superclass superclasses superuser
swagger symlink symlinks synchronize syscall syscalls syslog tcp tcpdump telnet
tensorboard terraform testcase testcases testsuite textfield textfields
throughput thunderbolt timestamp timestamps tls tokenize tokenizer tokenizers
tombstone tombstones traceback tracebacks traceroute transcode transcompile
transpile transpiler transpilers treemap treemaps truncate truncation tsconfig
tuple tuples typescript udp undeploy unicode unittest unittests unmount unpack
unserialize uplink uplinks upstream uptime upsert upserts vagrant validator
validators vdom vectorize vectorization vhost vhosts viewport viewports
virtualize visualization visualizations vmware vnc voip vpc vpcs vulkan
watchdog watchdogs webassembly webgl webhook webhooks webrtc webview webviews
whitelist whitelists whitespace wideband winapi wireguard wireshark xcode
xdebug xhtml xpath xquery xss yaml yum zabbix zeromq zfs zookeeper
""".split()

ACADEMIC = """
abstract abstraction academia academic academically accreditation accredited
adjunct admissions affirmative allegory alliteration alumni alumnus ambiguity
analogous analogy analysis analytical analyze annotate annotation annotations
anthology anthropology antithesis appendix applicant aptitude archetype
argument argumentative articulate assertion assessment assimilate assumption
attrition autonomous bibliography biographical biology biotechnology bourgeois
breadth byline calibrate campus campuses canon canonical capstone catalogue
causal causality certify characterization chronological citation cite civic
clarity classical cognition cognitive cohort collaborate collaboration
collaborative collateral colloquial colloquium commentary communal comparative
competence competency complement complementary complexity comply composition
comprehensive compulsory conceive conceptual conceptualize concise conclude
conclusive concurrent confer conferring connotation consensus consent
consequent considerable consolidate constituency constituent constraint
construct consult consultation contemporary contend contention context
contextual continuum contradiction contradict controversy conversion
cooperative correlate correlation correspond correspondence corroborate
credible criterion critique culminate cumulative curriculum curricula
dean debate deconstruct deductive deduce deficiency demographic demonstrate
denominator denote depict derive descriptive designation determinant devise
devote diagnostic dialect dichotomy differentiate differentiation diminish
diplomacy discourse discrepancy discrete discretion discriminate displacement
disproportion dissect disseminate dissertation distinct distinction diverse
diversity doctrine domain dominate draft dynamic dynamics ecological ecology
elaborate elective eliminate eloquent empirical enable endeavor endorse
enlighten enrollment entity enumerate epistemology equate equitable equivalence
eradicate erode essential ethical ethics ethnic ethnicity evaluate evaluation
eventual evidence evident evolve exacerbate exceed excerpt exclude exclusion
executive exemplify exert exhaust exhibit exhibition expansion explicit
explicitly exploit exploration expose exposition extract extrapolate
facilitate faculty feasible finite fluctuate formidable formulate forthcoming
fraction framework fraternity fulfil fundamental fundamentally generalize
generate generic genesis genre geographic geopolitical global globalization
governance gradual graduate granular groundwork guideline habitat heterogeneous
hierarchy holistic homogeneous humanities hypothesis hypothesize hypothetical
ideology illuminate immerse immigration immune imperative implication implicit
implicitly impose impoverish incidence incline inclusive incorporate
incremental incumbent indigenous infer inference influx inherent inhibit
initiate initiative innate innovation innovative input inquiry insightful
instill institutional integrate integration integrity intellect intellectual
interact interaction interdisciplinary interim interpret interpretation
intervene intervention intricate intuitive invariably investigate invoke
isolate isolation journal juxtapose juxtaposition kinesthetic labor landscape
lateral lecture legitimate liberal liberate liken likewise linear linguistic
literacy literal literally longitudinal lucid mainstream mandate mandatory
manifest manipulate manuscript marginalize maximize mediate medieval mentor
meritorious metaphor methodology migration millennium minimize misconception
module monitor monograph moreover motivation multidisciplinary multifaceted
municipal mutual narrative negligible negotiate neutral niche nominal nominate
norm notable notwithstanding nuance objective obligate oblige obscure
observation obsolete obtain occupational offset ongoing operational optimum
orientation orthodox outweigh overarching overlap oversee paradigm paradox
parallel parameter paraphrase participate participation passive pedagogy
perceive percentage periodical persist perspective pertinent phenomenon
philosophical placement plausible pluralism polarize policy portfolio posit
postulate potent potential pragmatic precede precedent precise predominantly
preliminary premise prestige prevalent principle privilege procedure proceed
procurement profound prohibit prominent prone propaganda propensity proponent
proportion prospect protocol provoke proximity prudent pseudo publication
qualitative quantify quantitative query radical rationale rationalize realm
reckon reconcile redundant regime reinforce reiterate relevance relevant
reluctance render repercussion replicate repository represent reproduction
requisite resemble reside resolution resolve respective restrain retain
retention retrieve reveal revelation revenue revise revision rhetoric rigorous
rudimentary scenario schema schematic scholarly scrutiny secular semantic
seminar sequential session settler significance simulate simultaneously
skepticism socioeconomic solely solicit solidarity sophisticated sovereign
specialize specification spectrum speculate stabilize stance standardize
statistic statistical stereotype stimulate stipulate straightforward strategy
structural subordinate subsequent subsidy substantial successor sufficient
sufficiently summarize superficial supplementary suppress supremacy surplus
surveillance sustainable syllabus symbolic sympathize synchronous syndrome
synthesis systematic tactical tangible temporal tentative terminate terminology
textbook thematic theorem theoretical thereby thesis threshold trajectory
transform transient transition transmit transparent traumatic tremendous
trivial tutorial unanimous undergo undermine undertake undoubtedly unify
unprecedented utilize validity variable variation vehicle verbal verification
verify versatile vicinity virtual virtually visual vocabulary voluntary
vulnerability vulnerable warrant whatsoever whereby widespread
""".split()

BUSINESS = """
accountant accounting accrual acquisition actuary adjuster administrator
advertise advertisement advertising affiliate agenda aggregate allocate
amortization analyst analytics annual annuity appraisal appreciate
appropriation arbitrage arrears asset assets attrition audit auditor
authorize automate automation bailout balance bankrupt bankruptcy baseline
benchmark beneficiary bidder bilateral billing blueprint board bondholder
bookkeeping bottleneck brand branding breakeven brokerage budget budgetary
bureaucracy buyout capacity capitalization capitalize cartel cashflow catalyst
centralize certification chairman chancellor charter clause clearance client
clientele cluster cofounder collateral commerce commercial commission
commodity compensate compensation competitive competitor compliance comply
conglomerate consign consolidate consortium consultancy consumer consumption
contingency contract contractor convene copyright corporate corporation
corrective cost counterfeit coupon credential creditor crowdfund crowdfunding
cumulative custodian customize debit debt debtor decentralize deduction
default deficit delegate delegation deliverable demand demographic depreciate
depreciation deregulate derivative designate developer diligence disbursement
disclaimer discount discretionary distribution distributor diversify divestiture
dividend doctrine documentation downgrade downsizing downswing downstream
downturn draft due duration earnings ecommerce econometric economist economy
efficiency embargo embezzle emolument employee employer empowerment endorse
endorsement enterprise entrepreneur entrepreneurial entrepreneurship equity
escalation escrow estate estimate evaluation exceed excise exclusivity
execution executive exempt exemption expenditure export exporter facilitate
factoring fiduciary finance financial fintech fiscal flagship forecast
foreclosure forfeit franchise franchisee franchisor fraud freelance freelancer
fulfillment fundraise fundraiser furnish garnish garnishment gdp goodwill
governance gross guarantee guarantor hedge hedging hierarchy holding horizontal
hostile housekeeping hyperinflation illiquid implication import importer
incentive inception incorporate incorporation increment incubate incubator
indemnify indent indenture independent indicator indirect industrial industry
inflate inflation infrastructure infringe infringement initial insolvency
insolvent installment institutional insure insurer intangible interest interim
intermediary inventory invest investee investor invoice ipo itemize itinerary
joint journal jurisdiction jurisprudence kickback labor landlord layoff lease
leaseholder ledger lender leverage leveraged liability liable license lien
liquidate liquidation liquidity listing litigation lobby lobbyist logistics
longterm loss lucrative lump macroeconomic mandate manufacturer margin
marginal markdown markup maturity mediate mediation memorandum mentor
mercantile merchandise merchant merger microeconomic middleman milestone
minority mogul monetary monetize monograph monopoly moratorium mortgage
multinational municipal mutual negotiate negotiation networth nonprofit
notarize obligation offshore ombudsman onboard onboarding operating operational
optimize option outsource outsourcing overcharge overdraft overhead overrun
oversee oversight overtime overturn owe ownership pact par paralegal
partnership patent patronage payable payback payday payee payment payoff
payroll penetration pension percentage perquisite personnel petition pipeline
placement plaintiff pledge plummet portfolio precedent premium prepayment
principal privatize probation proceeds procurement productivity proficiency
profit profitable program projection promissory promote promotion proprietary
prospect prospective prospectus protocol provision proxy public purchase
purchaser pursue qualification quarterly quorum quota quotation rally ratify
ratio ration reallocate realtor rebate receipt receivable recession
reciprocal recommend recruitment redemption redundancy refinance reform
refund reimburse reinsurance reinvest remit remittance remunerate render
renewal renovate rental reorganization repayment replenish repossess
repurchase requisition residual resignation resource restructure retail
retailer retain retainer retention retire retirement retroactive return
revenue revocable revolving rider rollout roster royalty salary salvage
sanction scalable scale schedule securities securitize seniority settlement
shareholder shortfall shutdown signatory sizable solvent sourcing speculate
speculation sponsor sponsorship stakeholder startup statutory stipend
stipulate stockpile strategic streamline structural structure subcontract
subcontractor sublease submission subordinate subscribe subscription
subsidiary subsidize subsidy succession summit superannuation supplement
supplier supply surplus surcharge sustainable swap syndicate synergy
takeover tangible tariff taxation taxpayer telecommute tenant tender tenure
terminate termination threshold throughput timeline toll trademark tranche
transaction treasury trend tribunal trust trustee turnover turnaround
unanimous underperform undervalue underwrite underwriter unemployment
unilateral union upfront upgrade upmarket upstream uptick utility vacancy
validate valuation variable vendor venture vertical vested viable volatility
volume voluntary voucher waiver warehouse warranty wholesale withdrawal
workforce workload writeoff yield zero
""".split()

MEDICAL = """
abdomen abdominal abscess acetaminophen acne acute adenoma adhesion adrenal
adverse agonist ailment airway allergen allergic allergy allograft alopecia
ambulance ambulatory amino amnesia amniotic amputation analgesia analgesic
anatomy anemia anesthesia anesthesiologist aneurysm angina angioplasty ankle
anomaly anorexia antacid antagonist antenatal anterior antibiotic antibody
antidepressant antidote antifungal antigen antihistamine antimicrobial
antioxidant antiseptic antiviral aorta apnea appendectomy appendicitis
arrhythmia artery arthritis arthroplasty aspiration asthma astigmatism
atherosclerosis atrium atrophy autoimmune autopsy bacteria bacterial bandage
bariatric benign bilateral bile bilirubin biomarker biopsy bipolar bladder
blister bloodstream bone bowel brace bradycardia brain bronchial bronchitis
bruise bypass calcium calculus cancer cancerous capillary capsule carcinoma
cardiac cardiology cardiopulmonary cardiovascular cartilage cataract catheter
cavity celiac cerebral cervical cervix cesarean chemotherapy chest cholesterol
chromosomal chromosome chronic circulation cirrhosis cleft clinical clot
coagulation cochlear colic colitis colon colonoscopy coma comorbidity
complication compress concussion congenital congestive conjunctivitis
connective consciousness consent constipation consultation contagious
contraception contraceptive contraction contraindication contusion convulsion
cornea coronary cortex cortisol cosmetic cranial creatinine croup cyst
cytology debridement decongestant deficiency defibrillator deformity
degeneration dehydration dementia dental dentist dermal dermatitis
dermatology detoxification diabetes diabetic diagnosis diagnostic dialysis
diaphragm diarrhea dietary digestion digestive dilation disability discharge
discomfort dislocation disorder disorientation dissection distension diuretic
dizziness donor dopamine dosage dose drainage dysfunction dyslexia dysplasia
earache eczema edema electrocardiogram electrolyte embolism embryo emergency
emission empathy enamel encephalitis endocrine endometriosis endoscopy enzyme
epidemic epidemiology epidermal epidural epilepsy epinephrine erythrocyte
esophagus estrogen euthanasia examination excision excrete exhalation
expectorant extremity eyesight fallopian fatigue fatty fecal femoral femur
fertility fetal fetus fever fibrillation fibromyalgia fibrous fissure
fluoride follicle formulary fracture frontal frostbite fungal fungus
gallbladder gallstone gangrene gastrectomy gastric gastritis gastroenterology
gastrointestinal genetic genital genome geriatric gestation gland glaucoma
globulin glucose gluten glycemic gout graft groin gynecology hallucination
handicap hazardous heartbeat heartburn hematology hemoglobin hemorrhage
hemorrhoid hepatic hepatitis herbal hereditary hernia herpes holistic
homeopathy hormonal hormone hospice hospitalize hydration hygiene hypertension
hyperthyroidism hypoglycemia hypotension hypothermia hypothyroidism
hysterectomy ibuprofen icu imaging immune immunization immunodeficiency
immunology impairment implant incision incontinence incubation indication
indigestion infarction infectious infertility inflammation influenza infusion
ingest inhaler injection inpatient insemination insomnia insulin intensive
intercostal internal intestinal intestine intracranial intravenous invasive
iritis irrigation ischemia isolation jaundice jejunum joint juvenile
keratitis ketamine kidney kneecap labyrinth laceration lactation lactose
laryngitis larynx laser lateral lavage laxative lesion lethargy leukemia
leukocyte ligament limb liver lobe lobectomy lozenge lumbar lump lung
lymph lymphatic lymphocyte lymphoma magnetic malformation malignant malnutrition
mammogram mammography mandible mastectomy meatal medication membrane meningitis
meniscus menopause menstrual menstruation metabolic metabolism metastasis
microbe microbiology migraine mineral miscarriage molar molecule monitor
monoclonal morbidity morphine mortality mri mucous mucus multiple mumps
murmur muscular musculoskeletal mutation myeloma myocardial nape narcotic
nasal nausea nebulizer necrosis neonatal neoplasm nephrologist nephrology
nerve neural neuralgia neuritis neurology neuromuscular neuropathy
neurosurgeon neurosurgery neutrophil nicotine nodule noninvasive nsaid
numbness nurse nutrient nutrition obstetric obstetrician obstetrics ocular
oncology opaque operating ophthalmologist ophthalmology opiate opioid optical
optometrist optometry oral organ organic orthopedic orthopedics osmosis
ossification osteoarthritis osteopath osteoporosis otitis otolaryngology
outbreak outpatient ovarian ovary overdose overweight ovulation oximetry
oxygen pacemaker palate palliative palpation palpitation pancreas pancreatic
pandemic papilloma paralysis paralyze paramedic parasite parasitic pathogen
pathological pathologist pathology patient pectoral pedal pediatric
pediatrician pelvis penicillin peptic percutaneous pericardium perinatal
periodontal periosteum peripheral peristalsis peritoneum peritonitis
pharmaceutical pharmacist pharmacology pharmacy pharyngitis pharynx phlebotomy
phlegm phototherapy physical physician physiological physiotherapy pituitary
placebo placenta plaque plasma platelet pleural pleurisy pneumonia podiatrist
podiatry polyp postmortem postoperative postnatal postpartum potassium
precancerous precaution preeclampsia prenatal prescription preventive prion
probiotic procedure proctology prognosis prolapse prophylaxis prostate
prosthesis prosthetic protein protocol proton proximal pruritus psychiatric
psychiatrist psychiatry psychoactive psychological psychologist psychology
psychotherapy puberty pulmonary pulse puncture pupil purulent pus
quadriplegia quarantine rabies radial radiation radiograph radiologist
radiology radiotherapy rash receptor reconstruction recovery rectal recurrence
referral reflex reflux regimen rehabilitation rehydration relapse remission
renal reproductive resection residency resistance respiration respiratory
resuscitation retina retinal rhinitis rheumatoid rheumatology rib ribcage
rickets rotator rupture sacral sacrum saline saliva salivary sarcoma scalpel
scan scar schizophrenia sciatica scoliosis screening sebaceous secretion
sedation sedative seizure semen sensory sepsis septic septum serology serum
shin shingles sigmoid sigmoidoscopy sinus sinusitis skeletal skeleton
smallpox sodium sonogram sonography spasm specialist specimen sperm sphincter
spinal spine spirometry spleen splint sprain sputum staphylococcus sterilize
steroid stethoscope stimulant stimulus stitch stomach stool strain strep
streptococcus stress stricture stroke subcutaneous sublingual suction
superficial supplement suppository surgeon surgery surgical suture swab
swelling symptom syndrome syringe systemic systolic tarsal tendinitis tendon
terminal testicle testicular testosterone therapeutic therapist therapy
thermal thoracic thorax throat thrombosis thrombus thyroid tinnitus tissue
tomography tongue tonsil tonsillectomy tonsillitis topical tourniquet toxic
toxicology toxin trachea tracheotomy traction transfusion transplant trauma
traumatic tremor triage tricep tuberculosis tumor tumour tympanic ulcer
ulcerative ultrasound umbilical ureter urethra urinalysis urinary urine
urological urology uterine uterus vaccination vaccine vaginal valve varicella
varicose vascular vasectomy vein venous ventilation ventilator ventricle
ventricular vertebra vertebrae vertebral vertigo viral virology visceral
vitamin vitreous vocal vomit wart wrist xray zoonotic
""".split()

SCIENCE = """
abiotic absorption acceleration acoustic acoustics activation adaptation
adenine aerobic aerosol alchemy algebra alkaline allele alloy altitude
amalgam amber ammonia amorphous ampere amplitude analog anatomical angular
anion anode anomalous aquatic aqueous archaeologist archaeology argon
arithmetic arsenic asteroid astrophysics atmospheric atom atomic attenuation
aurora axis azimuth barometer basal basalt biodegradable biodiversity
bioenergy biofuel bioinformatics biological biomass biomechanics biomedical
biophysics biosphere biotic bismuth boiling bonding botanical botany boron
buoyancy cadmium calcify calibration calorimeter calorimetry cambrian
capacitance capacitor capillarity carbide carbon carbonate carnivore catalyst
catalyze cathode cation celestial cellular celsius centrifuge ceramic cesium
chloride chlorine chlorophyll chromatic chromatography chromium circuit
circuitry classify cleavage climatology clone coaxial cobalt coefficient
coexist coherent cohesion combustion cometary compound concave condensation
conductance conductive conductor cone conjugate conservation constant
constellation convection convergent conversion convex coordinate copolymer
copper corrosion cosmic cosmology cosmos coulomb covalent crystalline
crystallize curvature cyanide cyclone cytoplasm datum deceleration
decompose decomposition deduction defraction density deposition derive
desalination dialectical diffraction diffusion dilute dilution diode dioxide
displacement dissolution distillation divergent dna doppler dynamo dynamics
earthquake eclipse ecological electrode electrolysis electromagnetic
electromagnetism electron elemental elevation ellipse elliptical emission
emulsify endothermic entropy enzyme equatorial equilibrium equinox erosion
eruption ethanol eukaryote evaporate evaporation evolution excitation exponent
exponential extinction extraneous extrusion fabrication fahrenheit fauna
fermentation ferrous fission flora fluorescence fluorescent flux focal
formaldehyde formula fossil fossilize freezing frequency friction fulcrum
fusion galaxy gamma gaseous gastric gelatin gene genealogy generator
genomic genus geochemistry geodesic geographic geological geologist geology
geomagnetic geometric geophysics geothermal germination glacial glacier
glucose glycerol gradient granite gravitational gravitation gravity greenhouse
groundwater gyroscope habitat halide halogen harmonic hectare heliocentric
helium hemisphere herbicide herbivore hertz heterogeneous hexagonal histogram
hologram homogeneous hormone humidity humus hybrid hydraulic hydrocarbon
hydrodynamic hydroelectric hydrogen hydrology hydrolysis hydrostatic hydroxide
hyperbola hypothesis hypothetical ichthyology igneous immunity impedance
impervious impulse incandescent indicator indigenous inductance induction
inert inertia infrared inorganic insecticide insulator interaction
intercellular interference intergalactic interstellar invertebrate iodine ion
ionic ionize ionosphere irradiation isobar isomer isothermal isotope
isotropic joule kelvin kinematic kinetic lactic lanthanide larva laser
latitude lattice lava lead lepton levitation lichen ligand limestone
lithium lithosphere logarithm longitude luminescence luminosity lunar
macroscopic magma magnetic magnetism magnitude mammal manganese mantle
maritime mass matrix mechanics meiosis membrane mercury meridian mesa
mesosphere metabolism metallic metallurgy metamorphic metamorphosis
meteorite meteorology methane microbe microbiology microcosm micrometer
microscope microscopic millibar mineral mineralogy mitochondria mitosis
modulus moisture molar molecular molecule molten momentum monatomic
monocular morphology motion multicellular mutation nanometer nanotechnology
nebula neon neptune neutrino neutron newtonian nickel nitrate nitrogen
noble nomenclature nonrenewable nova nuclear nucleic nucleotide nucleus
nutrient oblique omnivore opaque optic optical orbit orbital ore organic
organism oscillation oscillator osmosis outcrop oxidation oxide ozone
paleontology parabola parallax paramagnetic parasite particle pascal
pathogen peninsula peptide percolation periodic permeability permeable
permutation perturbation petrify petroleum ph phosphate phosphorus photon
photosynthesis photovoltaic phylogenetic phylum physics piezoelectric pigment
planetary plankton plasma plate plutonium polar polarization pollinate pollen
polymer polymerization population porosity potassium precipitate predator
pressure primate primordial prism probability probe prokaryote propagation
propane propulsion protein proton prototype protozoa proximity pulsar purity
quadrant quantum quark quartzite radar radiation radioactive radiocarbon
radiometric rainfall reagent receptor recessive recombinant recycle redox
reef reflection refraction relativity renewable replication reptile residual
resin resistivity resonance retardation reversible revolve rift rna rotation
saline satellite saturation scalar scattering sediment sedimentary seismic
seismology semiconductor simulate siphon sodium solar solstice solubility
soluble solvent sonar sonic spatial species specimen spectrometer
spectroscopy spherical spore stagnation stalactite stalagmite static
statistical steppe stimulus stoichiometry stratosphere stratum structural
sublimation subterranean sulfate sulfide sulfur superconductor supernova
suspension symbiosis symmetry synapse synthesis synthetic tectonic telescope
temperate temporal tensile terminal terrestrial tertiary thermal
thermodynamics thermonuclear thermometer thorium tidal topography tornado
torque toxin trajectory transform transient transistor translucent
transmitter transparent transpiration tremor tributary tropics troposphere
tsunami tungsten turbulence turbulent ultraviolet unicellular upwelling
uranium vacuum valence vapor variable vector velocity ventilation vertebrate
vibration viscosity volatile volcanic volcano volt voltage vortex
wavelength weathering xenon zenith zinc zoology
""".split()

INFORMAL_SLANG = """
adorbs afk ama asap awesome bae basically bestie binge bingewatch bingeing
bless blog blogger blogging blogpost brag bragging brainwash brb bro bruh
btw buff buffed buzzkill cancel cancelled chill chillax clickbait clout
collab comeback comfy copypaste crappy cringe cringeworthy crush crushed
cyberbully dank daredevil debunk defriend derr diss ditto diy dm doomscroll
dope downvote dude dunno edgy email emoji envy epic eyeroll facepalm fam
fanboy fandom fangirl fave fiasco finesse fire flame flamewar fml fomo
foodie freebie friendzone ftw ghosting gif glam glow goalpost goofy gotcha
gram grind grownup gucci hacker hangout hashtag hater haze hazing headcanon
heartbroken highkey hillarious hmu holla homecoming humblebrag idk imo
influencer inspo intro irl jelly karen kek kickback laidback lame legit
lifestyle livestream lmao lmk lol lowkey lurk lurker meh meme merch meta
micromanage mindblown mindfuck mindset miscall mislead mixtape mod mojo
moodboard multitask nah narc nerd nerdy newb newbie ngl noob notorious
npc offline omg onboarding oneup oof optics otp outage overachiever
overthink overshare paywall peachy peeps persona phat photobomb pickup
playlist pmo pov pregame procrastinate procrastinator puke quirky rant
recap regift relatable rekt remote resonate retro retweet rip roast roomie
rushy salty savage selfie shade shady shipment shoutout simp situationship
sketch sketchy skip slay sleek smh snack snippet spam spamming stan stealth
stoked stream streamer sus swag swerve tba tbh tease text thirsty tho
throwback tl tmi totes toxic tread trending troll trolling tryhard turnt
tweep tweet twit twitterverse unbox unboxing unfriend unplug upvote vibe
vibes vibing viral vlog vlogger wack wholesome wifi woke yeet yolo
""".split()

NAMES_AND_PROPER = """
aaron abraham adam adrian africa african aiden alabama alaska albert
alexander alice alison amanda amber america american amy anderson andrew
angela anna anthony antonio april arabia arab arctic argentina armenian
arthur ashley asia asian atlanta atlantic august austin australia australian
austria bailey bangkok barbara beijing belgium benjamin berlin bethany betty
beverly billy bob bobby bolivia boston brandon brazil brazilian brian bridget
britain british brooklyn bruce budapest california cambodia cameron canada
canadian carl carlos carolina caroline catherine chad charles charlotte chen
chicago chile china chinese christian christina christopher claire clark
cleveland colin colombia colorado columbia connecticut connor craig crystal
cuba cuban czech dallas daniel danielle darwin david dawn delhi democrat
denmark dennis denver derek detroit diana diane django donald donna douglas
dubai dublin dylan ecuador edward egypt egyptian elizabeth emily emma england
english eric erica erik estonia ethan ethiopia eugene europe european evan
evelyn finland florence florida france francis francisco frank franklin
frederick french gabriel gary geneva george georgia german germany ghana
gilbert grace graham grant greece greek gregory guatemala haiti hamilton
hannah harrison harry harvard hawaii hawaiian hayden heather helen henry
hillary holland hong houston howard hungary ian iceland idaho illinois
india indian indiana indonesia iowa iran iranian iraq iraqi ireland irish
isaac isabella islamic israel israeli italian italy jack jackson jacob james
jamie jane janet japan japanese jason java jean jennifer jeremy jessica
jimmy joan joanna joel john jonathan jordan jose joseph joshua juan julia
julian julie junior justin kansas karen kate katherine kathleen katrina
kelly ken kenneth kenny kentucky kenya kevin kim korea korean kristin kumar
kuwait kyle larry latin laura lauren lawrence lebanon lee leo leon leonard
leslie lewis lily lincoln linda lisa logan london lorenzo louis louisiana
lucas lucy luis luther lynn madison madrid malaysia manchester manhattan
margaret maria marie mark martin maryland mason matthew maureen mauritius
max maya megan melissa memphis mexican mexico miami michael michelle
michigan mike milan milwaukee minnesota mississippi missouri mohammad molly
mongolia monica montana montreal morgan morocco moscow munich murphy murray
nancy naomi nashville nathan nepal netherlands nevada newark newton
nicaragua nicholas nicole nigeria noah norfolk norman north norway norwegian
oakland obama ohio oklahoma oliver olivia omar ontario oregon orlando oscar
ottawa oxford pacific pakistan palestinian palmer pamela panama paris
patricia patrick paul paula pearl pennsylvania persian peru peter
philadelphia philip philippine philippines phoenix pittsburgh poland polish
portland portugal portuguese princeton qatar rachel ralph raymond reagan
rebecca republican rhode richard richmond richmond robert robin roger roman
romania rome ronald rosa ross russia russian ruth ryan sacramento
sacramento salvador samoa samuel sandra sarah saudi scandinavia scott
scottish sean seattle sebastian senegal seoul serbia seth shanghai shannon
sharon sheila shelby shelley shirley singapore sophia spain spanish
spencer sri stanford stephanie stephen sterling steve steven stockholm
stuart sudan susan sweden swedish switzerland sydney sylvia syria taiwan
tampa tanzania taylor tennessee teresa terry texas thai thailand theodore
thomas timothy todd tokyo tom tony toronto travis trinidad troy tucker
tunisia turkey turkish tyler uganda ukraine ukrainian ulysses united
uruguay utah valentine vancouver venezuela vermont veronica victoria
vietnam vietnamese vincent vincent virginia vladimir wales walsh walter
warren washington wayne wellington wendy wesley william wisconsin
wyoming xavier yale yemen york zambia zimbabwe
""".split()

FOOD_COOKING = """
almond appetizer apricot artichoke arugula asparagus avocado bacon bagel
baguette banana barbecue barley basil baste bean beef beet berry beverage
biscuit blackberry blanch blend blender blueberry boil bone braised bread
breakfast brie brine broccoli broth brownie bruschetta brussels buckwheat
burrito butter butterscotch cabbage cafe cafeteria cake calamari calorie
candy cantaloupe caramel cardamom carrot casserole cauliflower celery cereal
ceviche cheddar cheesecake cherry chestnut chicken chickpea chili chive
chocolate chop chowder cilantro cinnamon citrus clam clove cobbler cocktail
coconut cod condiment confection cookie coriander corn cornbread couscous
crab cranberry cream croissant crouton crumble crust cucumber cumin cupboard
cupcake currant curry custard cutlery daikon dairy decaf delicatessen dessert
dill dine diner dinner dip doughnut dressing drizzle dumpling edamame
eggplant enchilada endive entree espresso falafel fennel feta fig fillet
flan flatbread flour fondue frankfurter frosting fruitcake fry fudge
garbanzo garlic garnish gazpacho gelatin gherkin ginger glaze gnocchi gouda
gourmet graham granola grapefruit gravy griddle grill grits grocery guacamole
gumbo gyro halibut hazelnut herb hibiscus hoisin honey honeydew horseradish
hummus icing ingredient jalapeno jam jasmine jelly jerky julienne kale kebab
ketchup kimchi kiwi knead lager lamb lasagna lavender leek lemon lemonade
lentil lettuce lime linguine loaf lobster lollipop lox lychee macadamia
macaroni macaroon malt mango maple margarine marinade marinate marmalade
marzipan mayo mayonnaise meatball meatloaf melon menu meringue mesclun
millet mince mint miso mocha molasses mole mousse mozzarella muesli muffin
mushroom mussel mustard mutton naan nacho nectarine noodle nutmeg oat oatmeal
olive omelet onion orange oregano oyster paella pancake panini papaya paprika
parmesan parsley parsnip pasta pastry pate patty peach peanut pear pecan
pepper pepperoni peppermint pesto pickle pie pilaf pineapple pistachio
pizza plantain platter plum poach polenta pomegranate popcorn poppy popsicle
pork porridge potato poultry praline prawn pretzel prosciutto protein
prune pudding pumpkin puree quiche quinoa radicchio radish raisin ramen
raspberry ravioli recipe relish rhubarb rib rigatoni risotto roast romaine
rosemary roux rye saffron sage salad salami salmon salsa sandwich sardine
sauce sauerkraut sausage saute scallion scallop scone seafood seasoning
sesame shallot sherbet shortbread shortcake shrimp simmer sirloin skewer
sliced smoked smoothie snack sorbet souffle sour sourdough soy spaghetti
sparkling spice spinach squash sriracha steak steam stew strawberry stroganoff
strudel stuffing succotash sundae sunflower sushi sweet syrup taco taffy
tahini tamarind tangerine tapenade tart tartare tater teriyaki thyme tilapia
tiramisu toast tofu tomato topping tortellini tortilla truffle tuna turkey
turmeric turnip vanilla veal vegetable venison vinaigrette vinegar wafer
waffle walnut wasabi watercress watermelon wheat whisk worcestershire wrap
yam yogurt zest ziti zucchini
""".split()

SPORTS_FITNESS = """
ace agility archery arena assist athlete athletic athletics backboard
backhand backstroke badminton ballpark bantam barbell baseline basketball
bat baton batting bench bicycle biking birdie block bobsled bodybuilding
bout bowling boxing breakaway breaststroke bullseye bunker bunt calf cardio
catamaran centerfield championship cheerleader circuit cleats clinch coach
coaching comeback competitively conditioning cornerback court cramp crawl
cricket crossbar crossfit crouch cycling cyclist dart deadlift decathlon
defense defenseman deke derby diamond discus dismount diver diving dodge
dodgeball downhill draft dressage dribble dropkick dugout dumbbell dunk
endurance endzone equestrian faceoff fairway fencing fielder figure finalist
fitness flyweight footwork forehand formula foul foursome freestyle fullback
fumble goalkeeper goaltender golf golfer gouge grand grandslam grasscourt
gridiron grip gym gymnast gymnastics halftime halfback hammer hamstring
handball handoff harness heptathlon highbar hike hitter hockey holdup homerun
huddle hurdle hurdles hydration infield inning javelin jockey jogging judo
jumprope karate kayak kayaking kickball kickboxing kicker lacrosse lane
lateral launch lawn liftoff lineup lob lob luge marathon marksman martial
match medalist medley middleweight midfielder minicamp motocross motocross
mvp net netball offside olympian olympics onside opponent outfield outfielder
overtime paddle paddling par parallel parkour pass passer pat penalty
pentathlon pickoff pinch pitcher pitching playoff plunge pole pommel
powerlifting practitioner preliminary prize pro proficient pullup punchout
punter pushup putt putter quarterback quiver racket racquetball rally
ranking rapid rebound receiver recruit referee regatta relay rematch
repetition replay ringside rookie rotation roundhouse rowing rugby rung
runner running rusher sack sail sailing scooter scorer scrimmage scrum
sculling semifinal server serve setpiece shinpad shoot shooter shortstop
shotput shuttlecock sideline skeleton ski skiing skipper sled slalom
snowboard soccer softball sparring speedboat speedskating spike spikeball
sportsmanship sprint sprinter squash stadium stamina standings starter
steeplechase stirrup stopper stopwatch straight streak stretching stride
striker stroke strongman stunt substitute surf surfboard surfer surfing
sweeper swimming swimsuit synchronized tackle tailback takedown teammate
tennis tiebreaker timeout toboggan touchback touchdown tournament track
tracksuit trainer training trampoline trapeze treadmill triathlon triple
tryout tumbling turnover umpire underdog underwater undefeated uniform uphill
uppercut vault velodrome volleyball volley wakeboard warmup waterpolo
weightlifting wicket winger workout wrestling wrist yoga
""".split()

EMOTIONS_PERSONALITY = """
admirable admire affection affectionate agitate agitation agonize agreeable
alarmed alert alienate amiable amicable amuse analytical angst anguish
animated annoyed antagonistic anticipation anxious apathetic apologetic
appreciate apprehensive approval arrogant assertive astonish astonished
attentive aversion bashful belligerent bewilder bewildered bitter bliss
blissful bold bored bravery brooding bubbly calm capable captivate carefree
careless cautious charismatic charming cheerful childish clueless cocky
compassion compassionate compel complacent composed concerned confident
conflicted conscience conscious considerate contempt contemptuous content
contrite cooperative courageous courteous cowardly cranky creative cruel
curious cynical daring deceitful decisive dejected delighted demanding
dependable depressed despair desperate despise determined devastated devoted
dignified diligent disappoint discourage discriminate disdain disgust
dismay displeased disposition distress distrustful dominant domineering
doubtful dread driven eager earnest eccentric ecstatic edgy effervescent
egotistical elated embarrass emotion emotional empathy emphatic emptiness
enchant encouraged endear energetic enigmatic enjoyable enraged enthusiastic
envious euphoria exasperated excitable excited exhausted exhilarated
expectant expressive extravagant extrovert exuberant faithful fanatical
fascinate fearful fearless feisty fervent fickle fierce flustered focused
fond foolish forbearing forgiving forlorn frantic frazzled frightened
frivolous frustrated fulfillment furious fussy generous genial gentle genuine
giddy glad gleeful gloomy glum graceful gracious gratitude grim grouchy
grumpy guilt guilty gullible happiness harsh hasty hateful haughty
heartfelt hearty helpless hesitant honorable hopeful hopeless horrified
hostile humane humble humiliate humility humor humorous hysterical idealistic
ignorant imaginative immature impartial impatient impetuous impish impulsive
inadequate incredulous indecisive indifferent indignant industrious infatuated
inferior infuriated inhibited innocent insecure insensitive inspired
insufferable insular intense intentional intolerant introspective introverted
intuitive inventive irate irked irrational irresponsible irritable irritated
isolated jaded jealous jittery jovial joyful joyous jubilant judgmental keen
kindhearted kooky languish lethargic levelheaded lighthearted lively lonely
longing lost lovable loving loyal lustful mad malicious manipulative meek
melancholic melodramatic merciful meticulous miserable modest moody morose
motivated mourn mournful mystified naive narcissistic nasty naughty neglected
negligent nervous neurotic noble nonchalant nostalgic nurturing obedient
obnoxious obsessed obstinate offended open opinionated optimistic ornery
ostracized outgoing outrageous outspoken overwhelmed pacify panicked paranoid
passionate passive patient patronizing peaceful pensive perceptive perky
perplexed perseverant persistent persuasive pessimistic petulant philosophical
placid playful pleasant poised polite pompous possessive pragmatic prideful
principled proactive productive proud provocative prudent punctual puzzled
quarrelsome querulous quiet radiant rancorous rational reassuring rebellious
receptive reckless reflective regretful relaxed relentless reliable relieved
reluctant remorseful resentful reserved resilient resolute resourceful
respectful restless revengeful reverent rigid romantic ruthless sadistic
sanguine satisfied saucy scornful secretive seductive selfish sensitive
sentimental serene serious severe shameful sheepish shocked shy sincere
skeptical sly snobbish sociable solemn somber soothing sore sorrow sorrowful
soulful spirited spiteful spontaneous squeamish stable stern stimulated stoic
stressful strict stubborn studious stunned submissive sulky sullen supportive
sure surprised suspicious sympathetic tactful temperamental tenacious tender
tense terrified thankful thoughtful thrilled timid tolerant tormented
touchy tranquil trembling triumphant troubled trusting trustworthy turbulent
unassuming uncertain uncomfortable unconventional understanding unfriendly
ungrateful unhappy uninhibited unique unruly unsettled unstable upbeat uptight
valiant vengeful vibrant victorious vigilant vindictive virtuous vivacious
volatile vulnerable wary weary weepy welcoming whimsical willful wistful
withdrawn witty woeful wonderful worried worthless worthy wrathful yearning
zealous
""".split()

HOUSEHOLD = """
afghan air alarm alcove aluminum antenna apartment appliance armchair armoire
attic awning balcony banister barbecue barn basin basket bathrobe bathtub
beam bedding bedframe bedroom bedsheet bedside bedspread bell bench bin
birdbath blanch blanket bleach blinds bolt bookcase bookshelf bottle bracket
braid broom bucket bulb bunny bureau burner cabinet cage calendar candle
candlestick canister canopy carafe carpet carpeting carport cedar ceiling
cellar cement ceramic chain chair chaise chandelier charcoal chest chimney
china chore cistern clamp cleanser closet clothesline coaster comforter
compactor conditioner console container cooker cooktop cord cork corner
cornerpanel corridor couch counter countertop courtyard cozy cradle crate
credenza crib cubboard cupboard curtain cushion cutlery damper deck decor
decoration den desk detergent dimmer dishcloth dishpan dishwasher dishwater
dispenser doorbell doorknob doormat doorstep doorway downspout drain drainpipe
drape drawer dresser driveway drum dryer duct duster dustpan eave electric
elevator enclosure exterior faucet featherbed fence fender figurine filter
finial fireplace fixture flashlight flat flex floor floorboard flooring flower
flowerpot fluorescent foam foil footstool foundation foyer frame freezer
frieze frost furnace furnishing furniture futon gadget garage garland garment
gasket gate generator grate grout gutter hallway hammer hamper handrail hanger
hardware hardwood headboard heater hedge hinge hob hood hook hose humidifier
hutch hydrant incense insulation intercom interior iron ironing ivory jalousie
jamb jar joist junction jute kettle key kickstand kitchen knit knob ladder
lamp lampshade latch lathe laundry lawn lawnmower leaseholder ledge light
linen linoleum lock locker loft louver lumber mailbox mantel marble mat
mattress mirror molding mop mortar mosaic mount nailgun napkin nightstand
nursery ottoman oven pad padlock paint pail pane panel paneling pantry patio
pavement peg pellet pergola pillar pillow placemat plank planter plaque plate
platter pliers plug plumb plumber plumbing plunger polish porch porcelain
poster pottery projector prong prop propane pulley quilt rack rafter railing
ranch range recliner refrigerator remodel renovation rig rinse rod roller
roof rooftop rug runner sconce screen screw screwdriver shaker shampoo shed
shelf shingle shower shutter siding sill silverware sink skylight slab slate
slider slot smock sofa soffit solar soot spa spacer spade spackle spatula
spigot spindle sponge spoon spotlight sprinkler stain stainless staircase
stairs stairway stall stand staple step stepladder stilt stool storage stove
stucco sump sunroom switch tablecloth tabletop tack tape tarp terrace thatch
thermostat threshold tile timer tinfoil toaster toilet toolbox torch towel
transformer transom trash trellis trim trough trunk tub tumbler turret
umbrella upholstery utility vacuum valance valve vanity vault vent ventilator
veranda vestibule vinyl wallpaper wardrobe washer washroom waterheater
weatherstrip wheelbarrow wicker windowpane windowsill wiper woodwork
workbench wrench yard
""".split()

NATURE_WEATHER = """
acorn algae alpine altitude amber archipelago arid ash atmosphere avalanche
badlands bamboo bark basin bay bayou beach bedrock berm biodiversity biome
birch blaze blizzard bloom blossom bog boulder bramble branch breeze brook
brushfire bud butte buttercup canopy canyon cape cascade cave cavern chalk
channel chaparral chasm cliff climate clover cloud cloudbank cloudburst
coastal coastline cold conifer continent coral cove crater creek crest crevice
current cyclone dale dam dawn daybreak daylight debris delta dew dewdrop
dinghy downstream drift driftwood drizzle drought drumlin drylands dune
dusk dusty earthquake easterly ebb ecology eddy elderberry elm ember emission
equator erosion estuary eucalyptus evergreen extinction fauna fern fjord
flame flare flat flint flood floodplain flora flourish flow flower fog
foliage foothills forest forestry frost frostbite gale geyser glacier glade
glen gorge granite grassland gravel grove gully gust habitat hail hailstone
harbor harvest haze hazel heath heather hedge highland hillock hillside
hollow horizon hot humidity hummus hurricane hydrosphere iceberg icecap icicle
inlet island isle ivy juniper kelp knoll lagoon lake landfall landmass
landslide latitude lava lavender leaf lichen lightning limestone littoral log
longitude lowland lunar magma mangrove marsh meadow mesa meteor mist monsoon
moonlight moor moraine moss mount mountain mountainous mudflat muskeg
nightfall nimbus northeast oasis ocean oceanic offshore orchard outcrop
overcast overgrowth oxbow ozone panorama parcel parkland pass pasture pathway
peak peat pebble peninsula permafrost petal pine pinnacle plateau playa
plume polar pollen pond pool prairie precipice precipitation preserve
primeval puddle quagmire quarry quicksand rain rainbow raincloud rainforest
rainstorm rapid ravine ray reef ridge rift rim riparian ripple river
riverbank riverbed rivulet rock rocky root sandbar sandstone savanna scenic
sea seabed seabreeze seascape seashore seaside season sediment shade shadow
shallow shoal shore shoreline shrub silt skyline sleet slope slush smog snow
snowbank snowcap snowdrift snowfall snowflake snowmelt snowstorm soil solstice
southeast southwest spring sprout squall stalactite stalagmite steep steppe
stone storm strait stratosphere stream streambed summit sun sunbeam sunburn
sundew sunflower sunlight sunrise sunset sunshine surf swamp swell temperate
temperature tempest terrain thicket thunder thundercloud thunderstorm tide
timber timberline topography torrent trace trail treeline tributary tropical
trough tsunami tundra twilight typhoon undergrowth upstream valley vegetation
volcanic volcano waterfall watershed wave wetland whirlpool whirlwind
wilderness wildfire wildlife wind windchill windstorm wintergreen woodland
zenith zephyr
""".split()

LEGAL = """
acquit acquittal adjudicate adjudication admissibility affidavit allegation
allege amend amendment amnesty annul annulment appeal appellant appellate
arbitrate arbitrator arraign arraignment arrest arson assault assent attorney
bail bailiff bankrupt bar barrister bench breach brief burden bylaw case
certiorari chambers charge charter circumscribe circumstantial citation civil
claimant clemency code codify coerce collusion commissioner commit committal
compensation complainant compliance compulsory conciliation concurrent
condemnation confiscate confrontation consent conservator conspiracy
constitutional consultation contempt contract contravention conviction convict
copyright coroner corpus counsel counterclaim counterpart court covert
criminal crossexamine custodial custody damages debenture decree decriminalize
deed defamation default defendant deferment defraud deliberate deliberation
delinquent depose deposition deprivation deputy detain detention deterrence
diminished directive disclosure dismissal disqualify dissent dissolution
district divestiture docket domicile duress edict emancipate embezzlement
enact enforce enforcement enjoin equitable escrow espionage evict eviction
evidence examination executor exonerate exoneration expiration expunge
extradite extradition eyewitness felony fiduciary file filing fine foreclosure
forensic forfeit forfeiture forgery fraud freehold fugitive gavel governing
grievance guardian habeas hearing hearsay homicide illegal immunity impanel
impeach impeachment imprison imprisonment incarcerate indict indictment
infraction injunction inmate innocence inquest inspection insurance
intellectual interlocutory interrogation intestate intrusion investigation
judge judgment judiciary jurisdiction jurist juror jury justice justifiable
juvenile kidnap larceny lawsuit lawyer legal legislation legislative
legislature leniency levy liability liable libel license lien litigant
litigate litigation magistrate malpractice mandate manslaughter martial
mediate misdemeanor mitigate moratorium motion murder negligence notarize
notary oath obligation obstruction offender omission opinion ordinance
overrule overturn pardon parole patent peal penalty perjury perpetrator
petition plaintiff plea plead pleading police policy possession power
precedent preliminary presumption probate probation proceedings proclamation
prohibition promissory proof prosecute prosecution prosecutor provisional
provocation proxy punitive quash recidivism recognizance recount redress
referendum regulation relinquish remedy repeal repossession restitution
restraint retainer retrial revoke ruling sanction search seizure sentence
settlement slander solicitor sovereign statute statutory subpoena sue
summation summon summons supplement surety surveillance suspect sworn tariff
tenant tenure testify testimony tort trademark tribunal trust trustee
unauthorized unconstitutional undertaking unlawful uphold verdict violation
warrant waiver will witness writ
""".split()

MUSIC_ART = """
abstract acapella accompaniment accompanist acoustic acrylic adagio allegretto
allegro alto amphitheater andante anthem aperture arabesque aria arrangement
artisan artistry artwork atelier atonal audio auditorium avantgarde baritone
baroque bass bassline bassoon baton beat beatbox beautify bellows blues
bohemian bolero bongo brass breakbeat bridge bronze brush cadence calligraphy
cameo canvas caricature carol carve cascade cello ceramic chamber chant chord
choreography chorus cinematic classical clay clef climax collage coloratura
comedy comic commissioned compose composer composition concerto conductor
conservatory consonance contralto contour contrast counterpoint craft
creative creativity crescendo critique crosshatch cubism curator curtain
cymbal dance dancer debut decibel decrescendo depict designer digitize diorama
dirge discord display dissonance documentary dramatist drawing drum drummer
drumstick duet dynamic easel eclectic edition elaborate emboss embroidery
encore engrave ensemble epic episode etch etching exhibit expressionism
expressionist eyelet fabricate fantasia figurative film filament finale
fingerboard fingerpick flute folk fontish forte fortissimo fresco fugue funk
gallery genre gesso gig glaze graffiti graphic harmony harp harpsichord
heritage highlight hip horn hymn illustration illustrator image imagery
impasto impressionism improvisation improvise instrumental interlude
intermission interpret jazz kinetic landscape legato libretto lithograph loft
lyric lyrics maestro mandolin manifesto manuscript masterpiece matte medley
melody memoir mezzo miniature minor minuet mixed mode modernism monochrome
monologue montage mosaic motif multimedia mural music musical musician mute
narrative nocturne notation note novel octave ode oil opera operetta opus
oration orchestra orchestrate ornament ornate overture paintbrush painting
palette panel pantomime paperback parody pastel pastoral patina patron
pedestal percussion performer perspective philharmonic photograph photography
pianist piano pictorial pigment pitch plaster playback playwright plywood
poem poet poetry pointillism polka polyphony pop portrait portraiture pose
poster pottery prelude premiere print printmaking profile program progressive
prologue prose protagonist psalm psychedelic publishing quartet quintet
realism recital recorder refrain reggae rehearsal relief remix renaissance
render rendition repertoire replica reproduction requiem resonance restoration
retrospective revival rhapsody rhythm rhythmic riff rock romance romantic
rondo rough satire scale scenic score screenwriter sculptor sculpture
semibreve serenade series shading silhouette silverwork sketch sketchbook
skit slate slur snapshot solo sonata sonnet soprano soundtrack staccato
stained stanza statuary statue stencil still stipple story string strumming
studio style suite surreal surrealism suspense sustained swing symphonic
symphony synth synthesizer tango tapestry tattoo technique tempo tenor
textile theater theatrical theme timber timbre toccata tone topography trance
tranquility treble trilogy trio troubadour trumpet tune tuning typography
undertone unfinished unison varnish vaudeville venue verse vibrato video vinyl
vintage viola violin virtuoso visual vivid vocal vocalist waltz watercolor
woodwind wording xylophone
""".split()

TRAVEL_GEOGRAPHY = """
abroad accommodation adventure airline airfare airplane aisle altitude
ambassador amenity amtrak anchor annex archipelago arrival arrival avenue
aviation backpack baggage balcony banquet basin bay bazaar beach berth
binoculars boarding boardwalk boat boulevard boutique bridge buffet bungalow
buoy bypass cabin campground canal canoe cape capsule caravan cargo carousel
castle catamaran charter chasm checkin checkout circumnavigate cityscape
cliff climate clubhouse coach coast coastal commuter compartment compass
concierge conductor congestion continent convertible corridor countryside
county courtyard cove craft crew cruise cruiser cul currency customs deck
delay depart departure desert destination detour diesel dinghy diplomat
directional disembark district dock dolphin dome domestic donkey downtown
drawbridge drifting driver dropoff dual dune duration eastbound economy
elevation embassy embark excursion expedition explorer expressway extension
fairway farmland ferry festival flagship flight flotilla flyover footbridge
footpath foreign foreigner fortress freeway freight frontier fuel gateway
glacier globe gondola gorge gps gravel guesthouse guide guidebook gulf habitat
halfway hamlet hangar harbor hatch hatchback haven helicopter heritage
highrise highway hike hilltop hitchhike homestead horizon hostel hotel
houseboat hub hut immigration inbound inhabited inlet inn interchange
intersection interstate island isle itinerary jetty junction kayak kayaking
knot lagoon lakeside landmark latitude layover leisure lighthouse lobby locale
locomotive lodge longitude lookout luggage mast meadow metro metropolis
mileage minivan moat monument motel motorboat motorhome motorway mountaineer
mountainous navigator nightlife nomad northbound oasis observation ocean
offshore onboard oneway orbit orient outbound overpass overseas overlook
pacific paddle paddleboat panorama park parkway passage passenger passport
path pathway pavilion peak peninsula permit pier pilgrimage pilot plateau
plaza pontoon port portal porthole postcard prairie precipitation promenade
province pueblo pullman raft railroad railway ravine recreation reef
reservation reservoir resort retreat ridge riverbank riverboat riviera
roadblock roadside roadtrip roam route runway rural safari sailing sailboat
sandbar sanctuary scenic seaboard seacoast seafarer seaport seaside seatbelt
shelter shipboard shipyard shore shoreline shuttle sightseeing signpost
skyline sleeper slope snowcap sojourn southbound sovereign speedboat standby
starboard station steamboat stopover strait subway summit sunburn sunroof
sunset surroundings taxi terminal terrain territory tide timetable tollway
topography tourism tourist township traffic trail trailhead tram transit
transport traveler trek trekking trolley tropical trough trunk tunnel
turnpike ultralight undergrowth underwater uphill upstream urban utensil
vacation valley vantage vehicle vessel viewpoint village vineyard visa vista
voyage wander watercraft waterfront waterway waypoint westbound wharf
wilderness windmill woodland yacht yachting yonder zone
""".split()

FASHION_CLOTHING = """
accessory alteration angora ankle apparel applique armband attire bandana
bangle bathrobe beanie belt bikini blazer blouse bodice bolero bonnet boot
boutique bow bowtie bra bracelet braid brassiere breeches bridal bridesmaid
brooch buckle bustle button caftan camouflage cape capri cardigan cargo
cashmere casual chambray chiffon clad clasp clip cloak clutch coat cocktail
collar collection cologne corduroy corset cosmetic costume cotton couture
cravat crepe crew crochet cuff cufflink dapper darts denim design designer
dinner double drape drawstring dress dressy dye earmuff earring elastic
embellish embroider ensemble espadrille evening eyelet fabric fad fashion
fastener faux fedora felt fiber filigree fitting flannel flare flat fleece
flip footwear formal foundation frill fringe frock fur gala garment garter
gauze getup glamorous glamour glitter glove goggles gold gown haberdashery
halter handkerchief hat haute headband headdress hemline herringbone hood
hoodie hosiery hue jacket jean jersey jewel jewelry jumper jumpsuit kaftan
khaki kimono kneecap knit lace lapel lariat latex laundry leather leggings
leisure linen lingerie locket look lounge maxi medallion mesh midi midriff
mink mitten moccasin monogram muffler muslin necklace needlework negligee
neon nightgown nylon outfit overalls overcoat oxford pageant paisley pajamas
pant panther parasol parka pashmina patchwork pattern pearl pendant perfume
petticoat pin plaid platform pleated pocket polo polyester poncho pouch
preppy print pullover pump purl purse rayon ready regal rhinestone ribbon
robe ruffle runway sash satin scarf sequin shawl sheer shimmer shirt
shoe shorts silk skirt sleeve slipper slogan smock snap sneaker sock spool
stitch stockings stole strapless stripe stud style suede suit sundress
suspender sweater swimwear taffeta tailor tank taper tassel tee textile
thread tie tight tights toga topcoat trench trim trouser trunk tunic
turban turtleneck tutu tweed umbrella undergarment underscore uniform
upscale urban varsity veil velour velvet vest vintage vinyl visor vogue
waistcoat wallet wardrobe watch weave wig wool wristband wristwatch zipper
""".split()

EDUCATION = """
absentee academic accredit achieve achievement adjunct admissions adolescent
advance algebra alumni analysis annual applicant apprentice aptitude
arithmetic assessment assignment associate attendance auditorium bachelor
blackboard booklet bursary calculus campus certificate charter chemistry
class classroom coeducation cognition collegiate commencement committee
competence comprehension compulsory concentration conferral convocation
counselor course coursework credential cumulative curriculum dean degree
department detention diploma discipline doctoral dormitory dropout economics
educate education educator elective elementary eligible endowment enrollment
essay evaluation exam examination excerpt excursion exercise expulsion
extracurricular faculty fellowship finance flunk forensic fraternity freshman
geography geometry global grade grading graduate graduation grant guidance
gymnasium headmaster higher homework honor honors humanities illiterate
instruction instructor intellect interdisciplinary intermediate internship
introductory junior kindergarten laboratory language learning lecture lecturer
liberal library literature magna major mandatory master matriculate
mentorship midterm minor montessori multicultural nursery objective oral
orientation outreach pedagogy peer percentage philosophy physical physics
placement plagiarism postdoctoral postgraduate practicum preschool prerequisite
president principal probation professor proficiency program progressive
project proctor provost psychology qualification quarter quiz reading
recommendation reeducation reference referral register registrar registration
remedial report research residency review rhetoric rotc rubric sabbatical
scholar scholarship scholastic schoolbook secondary semester seminar senior
sociology sorority special specialization standardized statistics stipend
student study subject substitute summa superintendent supplementary suspension
syllabus tablet teacher teaching tenure test textbook theory thesis
transferable transcript tuition tutor tutorial undergraduate university
valedictorian vocational volunteer yearbook
""".split()

COMMON_ABBREVIATIONS = """
aka approx avg dept diy etc faq govt hrs inc intl max mgmt min misc msg
org pkg prev qty ref rsvp tel temp tbd tbc vol vs apt assoc blvd cert corp
dist div est ext fyi govt info inst jr lang ltd mfg natl qty sr tech univ
""".split()

COMPUTING_EXTRA = """
accesspoint adware ajax anonymize applet autofill autosave backspace
bitmask bloatware bookmark bootup captcha chatroom checksum chromebook
clickthrough ciphertext cleartext clipboard codec codepoint colorspace
commandline compile concatenate crossplatform datacenter debugger defragment
dequeue denormalize deprecate deserialize devkit diskspace dithering
dpi enduser failsafe freeware frontend frontend geocache gigahertz
grayscale handoff hardcoded hashcode heapsize hitcount hostname hotswap
hyperlink iframe inband inline inode interoperability keyframe keyvalue
killswitch logfile lookup megahertz metadata middleware miscompile multicast
multitenant nameserver netcode offload onprem outbound overclock overfit
pagerank passthrough patchnote pixelart plaintext playtest pluggable
prebuilt preflight prefork preprocess printout readonly refcount reflog
reparse requeue resize rootfs runtime screenshot scrollbar seekbar serverside
shellscript sideload signup softlock sourcecode spellcheck stacktrace
stateless subdomain submodule subprocess superscalar swapfile syscall
taskbar textbox timestamp tooltip trackball transcode typeahead typesafe
unescape uninstall unmount unserialize upscale viewport vmware webview
whitespace wildcard wireframe workaround zipfile
""".split()

ANIMALS = """
aardvark albatross alligator alpaca anaconda anchovy angelfish ant anteater
antelope ape armadillo baboon badger bald barracuda bat beagle bear beaver
beetle bison bluebird boar bobcat buffalo bulldog bullfrog bunny buzzard
camel canary capybara cardinal caribou carp caterpillar catfish centipede
chameleon cheetah chickadee chimpanzee chinchilla chipmunk clam cobra cockatoo
cockroach cod collie condor coral corgi cougar cow coyote crab crane crawfish
cricket crocodile crow cuckoo dachshund dalmatian deer dingo doe dolphin
donkey dove dragonfly duck eagle eel egret elephant elk emu falcon ferret
finch firefly flamingo flea flounder fly fox frog gazelle gecko gerbil
giraffe gnu goat goldfish goose gorilla grasshopper greyhound grizzly
groundhog grouper grouse guinea gull guppy hamster hare hawk hedgehog heron
hippopotamus hornet horse horseshoe hound hummingbird husky hyena ibis
iguana impala jackal jackrabbit jaguar jay jellyfish kangaroo katydid kingfish
kingfisher kinkajou kiwi koala komodo krill ladybug lamb lark lemming lemur
leopard lion lizard llama lobster locust loon lynx macaw mackerel magpie
malamute mallard manatee mandrill mantis mare marlin marmot marten mastiff
meadowlark meerkat mink mockingbird mole mongoose monkey moose mosquito moth
mouse mule muskrat mustang mutt narwhal newt nightingale ocelot octopus
opossum orangutan orca oriole osprey ostrich otter owl ox panda panther
parakeet parrot partridge peacock pelican penguin perch pheasant pig pigeon
pike piranha platypus pointer pony poodle porcupine porpoise possum prawn
pronghorn puffer puffin pug puma python quail rabbit raccoon ram rattlesnake
raven ray reindeer retriever rhino roadrunner robin rooster sailfish
salamander salmon sardine scorpion seahorse seal shark sheep sheepdog shepherd
shrimp silverfish skink skunk sloth slug snail snake snapper spaniel sparrow
spider squid squirrel stallion starfish stingray stork sturgeon swallow swan
swordfish tapir tarantula tern terrier thrush tiger toad tortoise toucan
trout tuna turkey turtle viper vulture wallaby walrus warthog wasp weasel
whale whippet wildcat wildebeest wolf wolverine wombat woodpecker wren yak
zebra
""".split()

COLORS_MATERIALS = """
alabaster amber amethyst aqua aquamarine ash auburn azure beige berry bisque
black blonde blue blush bone brass brick bronze brown buff burgundy burnt
camel caramel cardinal carmine carnation celadon cerulean champagne charcoal
chartreuse cherry chestnut chocolate chrome cinnamon citron claret coal cobalt
cocoa copper coral cornflower cranberry cream crimson crystal cyan daffodil
damask dark denim desert dove drab dusty ebony ecru eggshell electric emerald
fern firebrick flax flesh fluorescent forest fuchsia garnet ginger glass
gloss gold golden granite graphite gray green grey hazel heather henna honey
hunter ice indigo ivory jade jasmine jet khaki lapis lavender lead lemon
light lilac lime linen magenta mahogany maize mango maroon mauve metallic
midnight mint mocha moss mulberry mustard natural navy neon nude ocher olive
onyx opal orange orchid oxide oyster pale papaya peach pear pewter pine
pink plum powder prussian puce pumpkin purple quartz raspberry raw red
redwood robin rose rosewood rouge ruby russet rustic rust sable saffron sage
salmon sand sandy sapphire scarlet seafoam sepia shadow sienna silver sky
slate smoke snow steel stone straw strawberry suede sunflower tan tangerine
taupe tawny teal terracotta thistle titanium tomato topaz truffle turquoise
ultramarine umber vanilla vermillion violet walnut wheat white wine wisteria
wood yellow zinc
""".split()


def morphological_expand(verb_roots, adj_roots, noun_roots):
    """
    Generate morphological variants only from known verbs, adjectives, and nouns
    using appropriate suffix rules for each part of speech.
    This avoids nonsense like 'aardvarkable' by being selective about which
    words get which suffixes.
    """
    expanded = set()

    # Verb inflections only
    for word in verb_roots:
        w = word.strip().lower()
        if not w or not w.isalpha() or len(w) < 3 or len(w) > 10:
            continue
        # Basic: +s, +ed, +ing
        expanded.add(w + "s")
        if w.endswith("e"):
            expanded.add(w + "d")        # love -> loved
            expanded.add(w[:-1] + "ing") # love -> loving
            expanded.add(w[:-1] + "er")  # drive -> driver
            expanded.add(w + "r")        # manage -> manager
            expanded.add(w[:-1] + "ation") # create -> creation
            expanded.add(w[:-1] + "able")  # love -> lovable
        elif w.endswith("y") and len(w) > 3 and w[-2] not in "aeiou":
            expanded.add(w[:-1] + "ied")  # carry -> carried
            expanded.add(w[:-1] + "ies")  # carry -> carries
            expanded.add(w + "ing")       # carry -> carrying
        else:
            expanded.add(w + "ed")
            expanded.add(w + "ing")
            expanded.add(w + "er")
            expanded.add(w + "ment")
            expanded.add(w + "able")

    # Adjective forms only
    for word in adj_roots:
        w = word.strip().lower()
        if not w or not w.isalpha() or len(w) < 3 or len(w) > 12:
            continue
        expanded.add(w + "ly")
        expanded.add(w + "ness")
        if w.endswith("e"):
            expanded.add(w + "r")
            expanded.add(w + "st")
        elif w.endswith("y") and len(w) > 3:
            expanded.add(w[:-1] + "ier")
            expanded.add(w[:-1] + "iest")
            expanded.add(w[:-1] + "ily")
            expanded.add(w[:-1] + "iness")
        else:
            expanded.add(w + "er")
            expanded.add(w + "est")

    # Noun plurals only
    for word in noun_roots:
        w = word.strip().lower()
        if not w or not w.isalpha() or len(w) < 3 or len(w) > 12:
            continue
        if w.endswith("s") or w.endswith("x") or w.endswith("z") or w.endswith("ch") or w.endswith("sh"):
            expanded.add(w + "es")
        elif w.endswith("y") and len(w) > 3 and w[-2] not in "aeiou":
            expanded.add(w[:-1] + "ies")
        elif w.endswith("f"):
            expanded.add(w[:-1] + "ves")
            expanded.add(w + "s")
        elif w.endswith("fe"):
            expanded.add(w[:-2] + "ves")
        else:
            expanded.add(w + "s")

    # Filter reasonable lengths
    expanded = {w for w in expanded if w.isalpha() and 3 <= len(w) <= 18}
    return expanded


VERBS_COMMON = """
abandon abbreviate abolish absorb abstain abuse accelerate accept
accommodate accompany accomplish accumulate accuse acknowledge acquire
activate adapt add address adhere adjust administer admire admit adopt
advance advise advocate affirm afford aggregate aggravate agree aid aim
alert align allocate alter amaze amend amplify analyze animate announce
annoy anticipate apologize appeal appear applaud apply appoint appraise
appreciate approach approve approximate argue arise arrange arrest arrive
articulate ascend ascertain assemble assert assess assign assist assume
assure attach attain attempt attend attract audit augment authorize automate
avail avoid awaken
backfire backup bail bake balance ban bargain base bear beat become
beg begin behave believe belong bend benefit bet bind bite blame blast
blend bless block blow boast bog boil bolster bond book boost borrow
boss bother bounce bow brace brand breach break breathe breed bridge
brighten bring broaden broadcast browse brush budget build burden burn
burst bury buy bypass
calculate calibrate call campaign cancel capitalize capture care carry
carve catalogue categorize caution celebrate centralize certify challenge
champion characterize charge chart chase chat check cheer cherish
circulate cite claim clarify classify clean clear climb cling close coach
code coexist collaborate collapse collect combat combine comfort command
commemorate commission commit communicate compare compel compensate
compile complain complement complete complicate comply compose compound
comprehend compromise compute conceive concentrate concern conclude
condemn conduct confer confess confide configure confirm confront
congratulate connect conquer consent conserve consider consist console
consolidate constitute construct consult consume contact contain
contemplate contend contest continue contract contradict contrast
contribute control convene converse convert convey convince cook cooperate
coordinate cope copy correct correlate correspond cost counsel count
counter couple craft crash crave crawl create criticize crop cross crush
cultivate cure customize cut
damage dare deal debate deceive decide declare decline decorate decrease
dedicate deem deepen defeat defend defer define defy delay delegate
deliberate delight deliver demand demonstrate deny depart depend depict
deploy deposit depress deprive derive describe deserve design designate
desire destroy detail detect determine develop deviate devise devote
diagnose dictate die differ differentiate dig diminish direct disagree
disappoint discharge discipline disclose disconnect discourage discover
discuss disguise dismiss display dispute disregard dissolve distinguish
distribute disturb dive diverge divide divorce document dominate donate
double doubt draft drain draw dream dress drift drill drink drive drop
dump duplicate dwell
earn ease edit educate elaborate elect elevate eliminate email embark
embarrass embrace emerge emit emphasize employ empower enable enact
encounter encourage endanger endorse endure enforce engage enhance enjoy
enlarge enlighten enrich enroll ensure enter entertain entitle equip
erase erect err escalate escape establish estimate evaluate evade
evolve exaggerate examine exceed exchange excite exclude excuse execute
exercise exert exhaust exhibit exist expand expect expedite expel
experience experiment explain explicate exploit explore export expose
express extend extract
fabricate face facilitate fade fail fake fall familiarize fancy fascinate
favor fear feature feed feel fight figure file fill filter finance find
fine finish fire fit fix flag flash flee float flood flow fluctuate fly
focus fold follow forbid force forecast foresee forgive form formalize
formulate foster found frame franchise freeze frighten frustrate fuel
fulfill function fund furnish fuse
gain gamble gather gauge gear generate gesture give glimpse globalize
govern grab grade grant grasp greet grieve grind grip ground grow
guarantee guard guess guide
halt handle hang happen harbor harm harvest hasten hate haunt head
heal hear heat heighten help hesitate hide highlight hinder hint hire
hit hold honor hook hope host house humble hurt hypothesize
identify ignore illustrate imagine imitate immerse implement implicate
imply import impose impress improve improvise include incorporate
increase incur indicate induce influence inform inhabit inherit inhibit
initiate inject innovate input inquire insert insist inspect inspire
install institute instruct insulate insure integrate intend interact
interest interfere interpret interrupt intervene introduce invent invest
investigate invite involve irritate isolate issue itemize
join joke judge jump justify
keep kick kill kiss kneel knit knock know
label lack land last laugh launch lay lead lean leap learn leave lend
let level leverage lie lift light like limit line link list listen live
load lobby locate lock log look loose lose lower
magnify maintain manage mandate maneuver manifest manipulate manufacture
map march mark master match matter maximize mean measure mediate meet
melt memorize mention mentor merge merit mind minimize mirror miss mix
mobilize model moderate modernize modify monitor monopolize motivate mount
multiply murder
narrow navigate necessitate need neglect negotiate network neutralize
nod nominate normalize note notice notify nourish number nurture
obey object oblige observe obstruct obtain occupy occur offend offer
offset omit open operate oppose opt optimize orchestrate order organize
orient originate outline output outweigh overcome overflow overlook
oversee overturn overwhelm owe own
pace pack paddle paint panic parallel park part participate partner
pass patch pause pay peak penetrate perceive perform permit persist
persuade petition photograph phrase pick pilot pinpoint pioneer pitch
place plan plant play plead pledge plot plunge point polish pool pop
portray pose position possess post postpone pour practice praise pray
precede predict predominate prefer prejudice prepare prescribe
preserve preside press presume pretend prevail prevent print prioritize
proceed process proclaim produce profile profit program progress
prohibit project prolong promise promote prompt pronounce propose
prosecute protect protest prove provide provoke publish pull pump punch
purchase pursue push put puzzle
qualify question quit quote
race raid raise rally range rank rate reach react read realize reap
reason rebel rebuild recall receive recognize recommend reconcile
record recover recruit recycle reduce refer reflect reform refuse regard
register regret regulate reinforce reject relate relax release relieve
rely remain remark remember remind remove render renew rent repair
repeat replace reply report represent reproduce request require rescue
research resemble reserve reshape reside resign resist resolve resort
respond restore restrict result retain retire retrieve return reveal
reverse review revise revive reward rid ride ring rise risk rob
rock roll rotate ruin rule rush
sacrifice sake satisfy save scale scan schedule score scratch scream
screen seal search secure seek seem segment seize select sell send
sense separate serve settle shake shall shape share shelter shift
shine ship shock shoot shop shout show shut sigh signal signify
silence simplify simulate sing sit situate sketch skip slam sleep
slide slip slow smash smell smile snap solve sort sound spare speak
specialize specify speculate speed spend spin split sponsor spot
spread squeeze stabilize staff stage stagger stake stand standardize
stare start state stay steal steer stem stick stimulate stock stop
store straighten strain strategize stream strengthen stress stretch
strike strip strive structure struggle stuff submit subscribe
substitute succeed sue suffer suggest suit summarize supervise
supplement supply support suppose suppress surface surge surmount
surpass surprise surrender surround survey survive suspect suspend
sustain swallow swap swear sweep swim switch symbolize sympathize
synchronize synthesize
tackle tag tailor target taste tax teach tear telephone tell tempt
tend terminate test testify thank think threaten thrive throw tie
tighten tip tolerate top topple touch trace track trade train
transfer transform translate transmit transport trap travel treat
trigger trim triple triumph trouble trust try tune turn
undergo underline undermine understand undertake undo unfold unify
unite unlock unveil update upgrade uphold upset urge use utilize
validate value vanish vary venture verify view violate voice volunteer
vote
wade wager wait wake walk wander want warm warn wash waste watch
wave weaken wear weave weigh welcome whisper widen win wind wish
withdraw withstand witness wonder work worry worsen wrap write
yield
""".split()

ADJECTIVES_COMMON = """
abandoned able abnormal absent absolute abundant academic acceptable
accessible acclaimed accompanying accountable accurate accustomed
achievable acoustic active actual acute addictive additional adequate
adjacent administrative admirable adorable advanced adverse aesthetic
affordable aggressive agreeable agricultural airtight alert algebraic
alive alleged allied alone alternate alternative amateur amazing
ambitious american ample amused ancient angry annual anonymous anxious
apparent appealing applicable appropriate approximate arbitrary armed
aromatic artistic ashamed athletic atmospheric attached attractive
authentic automatic available average aware awesome awful
backward bad balanced bare basic beautiful beloved beneficial bent best
better big biological bitter blank bleak blessed blind blissful blonde
bold boring botanical bouncy brave brief bright brilliant broad broken
brown brutal budding busy
calm capable capitalist captive careful careless casual cautious cellular
central certain challenging charitable charming cheap cheerful chemical
chief chronic circular civic civil civilian clean clear clever clinical
close closed coastal cognitive cold collective colonial colorful colossal
comfortable comic commercial committed common communal compact comparable
compatible compelling competent competitive complementary complete
complex complicated comprehensive compulsory conceivable concrete
conditional confident confidential confirmed confused congressional
connected conscious consecutive conservative considerable consistent
conspicuous constant constitutional constructive contemporary content
continental continual continuous controversial convenient conventional
convincing cooperative corporate correct corresponding costly countless
courageous courteous cozy crazy creative credible criminal critical
crucial cruel cultural curious current customary cute
daily damn dangerous daring dark dead deadly dear decent decisive
decorative dedicated deep defensive definite deliberate delicate
delicious delightful demanding democratic dental dependent deplorable
depressed descriptive desirable desperate detailed determined devoted
different difficult digital diplomatic dire direct dirty disabled
disappointing disastrous disciplinary discrete distant distinct
distinctive distinguished diverse divine domestic dominant double
doubtful downtown dramatic drastic dreadful driving drunk dry dual
dull dumb durable dusty dutiful dynamic
eager early earnest eastern easy ecological economic educational
effective efficient elaborate elderly electric electronic elegant
eligible elite eloquent embarrassing emerging emotional empirical empty
encouraging endless energetic engaging enormous enthusiastic entire
entrepreneurial environmental epic equal equipped equivalent erratic
essential established eternal ethical ethnic evident evil evolutionary
exact exaggerated excellent exceptional excessive excited exciting
exclusive executive exhaustive exotic expensive experimental explicit
explosive extensive external extra extraordinary extreme
fabulous factual fading faint fair faithful familiar famous fancy
fantastic far fatal favorable favorite fearful feasible federal
fellow feminine fewer fierce fifth final financial fine firm first
fiscal fit fixed flat flexible floating fluent flying focal fond
foolish foreign foremost formal former formidable fortunate forward
fossil found fragile frank frantic free frequent fresh friendly
frightening front frozen fruitful full functional fundamental funny
furious future
gay general generous genetic gentle genuine geographic giant gifted
given glad glamorous global glorious glossy golden good gorgeous
governing graceful gradual grand graphic grateful grave great green
grim gritty gross growing guaranteed guilty
habitual half hallmark handsome happy hard harmful harmless harsh
hasty healthy heavy helpful heroic hidden high historic hollow holy
homeless honest hopeful horizontal horrible hostile hot huge human
humble humorous hungry identical idle ignorant illegal imaginary
immediate immense immune imperial implicit important impossible
improper inappropriate inclined inclusive incompatible incomplete
incredible independent indigenous indirect individual indoor
industrial inevitable inferior infinite informal inherent initial
inland inner innocent innovative insightful insignificant instant
institutional intact integral intellectual intelligent intense
interactive interim internal international intimate intricate
intuitive invasive investigative invisible involuntary involved
irrelevant irreversible isolated
joint judicial junior justifiable juvenile
keen key kind knowledgeable
large last lasting late lateral latter lazy leading lean learned
left leftist legal legendary legislative legitimate lengthy lesser
lethal level liberal lifelong light likely limited linear linguistic
liquid literal literary little live lively local logical lone lonely
long longtime loose loud loving low lower loyal lucky lunar
mad magnetic magnificent main mainstream major male manageable
mandatory manifest manual marginal marine marked married masculine
massive matching material mathematical mature maximum meaningful
mechanical medical medieval medium memorable mental mere metropolitan
middle mild military minimal minor minute mixed mobile moderate modern
modest molecular monetary monthly moral mounting multiple municipal
mutual mysterious
naive naked narrative narrow nasty national nationwide native natural
naval nearby neat necessary negative neighboring nervous net neutral
new nice noble nominal nonprofit normal notable notorious novel
nuclear numerous nutritional
objective obligatory obscure observable obvious occasional occupational
odd offensive official ok old ongoing online only open operating
operational opponent optical optimal oral ordinary organic original
other outdoor outer outgoing outstanding overall overseas overwhelming
own
painful pale parallel parental parliamentary partial particular
passive past patient patriotic peaceful peculiar pending permanent
perpetual persistent personal persuasive pessimistic petty
pharmaceutical philosophical photo physical pink plain planetary
plastic plausible pleasant plentiful plus pointed polar polished
polite political poor pop popular portable positive possible postal
potential powerful practical precise predictable predominant pregnant
preliminary premier premium prepared present presidential presumed
pretty prevalent previous primary primitive principal print prior
private probable productive professional profound progressive
prominent promising pronounced proper proportional proposed prospective
protective proud provincial provisional psychiatric psychological
public punctual pure purple
qualified quarterly quick quiet
racial radical random rapid rare rational raw ready real realistic
reasonable recent recovered red reduced regional regular regulatory
related relative relevant reliable religious reluctant remaining
remarkable remote renewable repeated representative reproductive
republican required reserved residential resistant respective
responsible responsive restored restricted retail retired reverse
revolutionary rich rigid risky robust romantic rough round routine
royal rude ruling rural
sacred sad safe satisfactory savage scared scattered scholarly
scientific seasonal secondary secret secular secure selective
semantic senior sensitive separate serial serious severe sexual
shallow shared sharp sheer short sick significant silent silly
similar simple simultaneous single situated skeptical skilled slight
slim slow small smart smooth social socialist sole solemn solid
solo sophisticated sorry sound southern spare spatial special
specific spectacular spiritual splendid spontaneous stable standard
standing stark static statistical steady steep sticky stiff still
strategic strict striking strong structural subjective subsequent
substantial subtle suburban successive sudden sufficient suitable
summary sunny super superb superior supplementary supportive supreme
sure surgical surplus surprising surrounding suspicious sustainable
sweet swift symbolic sympathetic synthetic systematic
tactical talented tangible technical teenage temporary tender terminal
terrific textual theological theoretical thick thin thorough tiny
tirelessly tolerable top total tough toxic traditional tragic
transitional tremendous tribal tropical troubled true trustworthy
ugly ultimate unable unanimous uncertain unclear unconstitutional
underground underlying unfair unfortunate unhappy unified uniform
unique united universal unknown unlikely unprecedented unusual upper
upstairs urban urgent useful usual utter
vacant vague valid valuable variable varied various vast verbal
vertical viable vibrant video vigorous vintage virtual visible visual
vital vivid vocal volatile voluntary vulnerable
warm weak wealthy weird welcome western wet whole wicked wide
widespread wild willing wise wooden worthy wrong
yearly young
""".split()

ADVERBS_COMMON = """
abnormally aboard abruptly absolutely abundantly accidentally accordingly
accurately actively actually adequately admittedly adversely afterward
afterwards again ago ahead alike almost alone along already also
alternatively altogether always annually anxiously anywhere apart
apparently approximately arguably artificially aside automatically
awkwardly
badly barely basically beautifully below besides better beyond blindly
bitterly boldly briefly broadly
calmly candidly carefully casually cautiously centrally certainly
characteristically cheaply cheerfully chiefly chronologically clearly
cleverly closely coincidentally collectively comfortably commonly
comparatively compassionately competitively completely conceptually
concretely conditionally confidently consciously consequently
conservatively considerably consistently constantly continuously
conversely convincingly coolly correctly correspondingly creatively
critically crucially culturally currently customarily
daily dangerously darkly decidedly deeply deliberately delicately
desperately diagonally digitally diligently directly disappointingly
discreetly distinctly domestically dramatically drastically duly
eagerly easily effectively efficiently electronically elegantly
emotionally emphatically endlessly energetically enormously entirely
environmentally equally especially essentially eternally even eventually
every everywhere evidently exactly excessively exclusively
exhaustively exclusively explicitly exponentially extensively
externally extraordinarily extremely
fairly faithfully famously fantastically fast fatally favorably
feverishly fiercely finally financially firmly firstly flatly fondly
forcefully forever formally formerly fortunately forwards frankly freely
frequently freshly frighteningly fully functionally fundamentally
furiously
generally generously gently genuinely gladly globally gradually
gracefully graphically greatly greatly grossly
halfway happily harshly hastily heartily heavily helpfully hereby
heroically hesitantly highly honestly hopefully hopelessly horizontally
horribly hourly hugely humbly hungrily hurriedly
ideally identically illegally imaginatively immediately immensely
impatiently implicitly importantly impressively improperly incidentally
increasingly incredibly independently indirectly individually inevitably
infinitely informally inherently initially innocently insistently
instantly intensely intentionally internally internationally intimately
invariably ironically irreversibly
jointly joyfully judiciously justifiably justly
keenly kindly knowingly
largely lastly lately laterally lavishly lazily legally legitimately
liberally lightly likely literally lively locally logically
lonely loosely loudly lovingly luckily
magically magnificently mainly manly manually marginally markedly
massively materially mathematically maximally meaningfully meanwhile
mechanically merely methodically mildly minimally miraculously
moderately modestly momentarily monthly morally moreover mortally mostly
multiply multiply mutually mysteriously
namely narrowly nationally naturally neatly necessarily negatively
nervously neutrally nevertheless newly nicely nightly nobly
nominally nonetheless normally notably noticeably notoriously
nowadays
objectively obligingly obscurely obviously occasionally officially
often once only onwards openly operationally optimistically ordinarily
organically originally otherwise outright outwardly overall overseas
overtly overwhelmingly
painfully partially particularly passionately passively patiently
perfectly periodically permanently personally persuasively
philosophically physically plainly pleasantly plus politically
poorly popularly positively possibly potentially powerfully
practically precisely predictably predominantly preferably
presumably previously primarily principally privately probably
profoundly progressively prominently promptly properly proportionally
proudly provisionally psychologically publicly punctually purely
purposefully
quarterly quickly quietly
randomly rapidly rarely rationally readily realistically really
reasonably recently recklessly regularly relatively reluctantly
remarkably remotely repeatedly reportedly respectively responsibly
restlessly retrospectively reversibly richly rightly rigidly
rigorously roughly routinely rudely ruthlessly
sadly safely satisfactorily scarcely scientifically seasonally
secondarily secondly secretly securely seemingly selectively
selfishly sensibly separately seriously severely sexually sharply
shortly sideways significantly silently similarly simply simultaneously
sincerely skeptically sleepily slightly slowly smoothly socially
softly solely solemnly solidly somehow somewhat somewhere soon
sophisticatedly specifically spectacularly spiritually spontaneously
squarely stably steadily steeply sternly still strategically
strictly strikingly strongly structurally substantially successfully
suddenly sufficiently suitably superficially supposedly surely
surgically surprisingly suspiciously sustainably sweetly swiftly
symbolically sympathetically systematically
tactfully technically temporarily tenderly terribly thankfully
theoretically thereby thoroughly thoughtfully tightly tirelessly
today together tomorrow tonight totally traditionally tragically
tremendously truly twice typically
ultimately unanimously uncomfortably unconditionally undeniably
underground undoubtedly unexpectedly unfairly unfortunately unhappily
uniformly uniquely universally unlikely unofficially unquestionably
unusually upstairs upward urgently usefully usually utterly
vaguely vainly vastly verbally vertically vigorously violently
virtually visually vitally vividly voluntarily
warmly weakly wearily weekly well westward whatsoever wholly widely
wildly willingly wisely wonderfully worldwide wrongly
yearly yesterday yet
""".split()

PREPOSITIONS_CONJUNCTIONS = """
aboard about above absent according across after against ahead along
alongside amid amidst among amongst anti apart around aside astride
atop away barring because before behind below beneath beside besides
between beyond but concerning considering despite down during except
excluding failing following for from given hence however including
inside into like minus near neither next notwithstanding off onto
opposite outside over owing past pending per plus providing regarding
round save since than through throughout till toward towards under
underneath unlike until unto upon versus via whereas while with within
without worth yet
""".split()

BODY_PARTS = """
abdomen achilles adam ankle anus aorta appendix arch arm armpit artery
back backbone belly bicep bladder blood bone bowel brain breast
breastbone buttock calf capillary cartilage cavity cheek chest chin
clavicle coccyx colon cornea cranium diaphragm disc duodenum ear
eardrum eardrobe elbow esophagus eyelash eyelid eyebrow face femur
finger fingernail fist foot forearm forehead gallbladder gland groin
gum gut hair hamstring hand head heart heel hip index intestine iris
jaw joint jugular kidney kneecap knuckle larynx leg ligament limb
lip liver lobe lung mandible marrow midriff midsection molar mouth
muscle mustache nail nape navel neck nerve nipple nose nostril
organ ovary palm pancreas pelvis phalanx pinky pupil rib sacrum
scalp shin shoulder skeleton skin skull sole spleen spine stomach
tendon thigh throat thumb thyroid tibia tissue toe tongue tonsil tooth
torso trachea tricep trunk tummy umbilicus uterus uvula valve vein
vertebra vocal waist womb wrist
""".split()

PROFESSIONS = """
accountant actor actuary administrator admiral advisor advocate agent
aide airman ambassador analyst anchor animator announcer appraiser
apprentice archaeologist architect artist assessor astronaut astronomer
athlete attorney auctioneer auditor author aviator babysitter baker
banker barber bargainer barista bartender bellboy biologist bishop
blacksmith blogger bodyguard bookkeeper botanist bouncer boxer brewer
broadcaster broker builder butcher butler cabinetmaker cameraman
captain caretaker carpenter cashier caterer ceo chairman chaplain
chauffeur chef chemist choreographer cinematographer clerk climber
coach cobbler comedian commander commissioner communicator companion
composer comptroller conductor confectioner consultant contractor cook
coordinator copywriter coroner correspondent counselor courier craftsman
crane creator critic curator custodian cyclist dancer dealer dean
decorator decorator defender dentist deputy designer detective
developer dietitian diplomat director disc dispatcher diver doctor
doorman drafter draftsman dramatist driver drummer drywall ecologist
economist editor educator electrician embassador emcee employee
employer embalmer engineer entertainer entrepreneur environmentalist
escort estimator evangelist examiner excavator executive exerciser
explorer exporter fabricator facilitator farmer farrier financier
firefighter fisherman fitter flavorist flight florist foreman forester
founder freelancer fundraiser gamekeeper gardener gemologist geneticist
geographer geologist glazier governor grader graphic groundskeeper
guard guide guitarist gymnast hairdresser handyman harbinger headmaster
herbalist herdsman historian homemaker horticulturist hostess hotelier
housekeeper hunter hydrologist hygienist illustrator importer
impresario indexer informant inspector instructor insurer interior
interpreter inventor investigator investor ironworker janitor jeweler
jockey journalist judge jurist keyboardist laborer landlord landscaper
launderer lawmaker lawyer lecturer lender lexicographer librarian
lifeguard linguist lobbyist locksmith lyricist machinist magistrate
maid maintenance manager manufacturer mapmaker marine marketer marshal
mason mathematician mayor mechanic mediator merchant messenger
meteorologist midwife miller miner minister missionary model moderator
monk mortician mountaineer mover musician nanny narrator naturalist
navigator negotiator neurologist notary novelist nurse nutritionist
obstetrician occupant officer ombudsman oncologist operator optician
optometrist orator organist orthodontist orthopedist osteopath
outfitter pacifist painter paleontologist paramedic paralegal
pathfinder pathologist patient patrolman patron pediatrician performer
perfumer periodontist permit pest pharmacist philanthropist philosopher
photographer physicist pianist pilot planner planter plasterer plumber
podiatrist poet police politician pollster porter postman postmaster
potter practitioner preacher president priest principal printer
probation producer professor programmer projectionist promoter
proofreader prosecutor prospector provider psychiatrist psychologist
publisher puppeteer quarterback rabbi radiologist rancher ranger
realtor receptionist recruiter rector referee reformer registrar
regulator rehab relative reporter representative researcher reservist
resident retailer reviewer ringmaster robotics roommate roofer runner
sailor salesman sampler sanitarian satirist scholar scientist scout
sculptor secretary security senator server serviceman shepherd sheriff
shopper singer skater skipper smuggler socialite sociologist soldier
solicitor songwriter speaker specialist speechwriter spokesperson
sportscaster spy statistician steward stockbroker strategist
student stylist superintendent supervisor surgeon surveyor swordsman
tailor taxidermist teacher technician technologist teller tenant
therapist tiler tinker trader trainer translator treasurer trooper
trucker trustee tutor typist umpire underwriter upholsterer usher
valet vendor ventriloquist veteran veterinarian videographer vintner
violinist vocalist volunteer waiter waitress warden warrior watchmaker
weaver welder wholesaler winemaker woodworker worker writer zoologist
""".split()

TIME_CALENDAR = """
afternoon annual annually april august autumn biannual bimonthly
biweekly calendar century chronological clock daily date dawn day
daybreak daylight daytime decade december delay duration dusk early
easter eighth eleventh epoch era evening fall february fifth first
fiscal fortnight fourth friday future halfhour holiday hourly
january july june leap lifetime march may midday midnight millennium
minute modern moment monday month monthly morning nanosecond
night nighttime ninth noon november october once overnight past
period perpetual postpone present prior quarterly saturday schedule
season second semester september seventh sixth spring subsequent summer
sunday tardy tenth third thursday tidy timeframe timeline timely
timeout timestamp timezone today tomorrow tonight triannual tuesday
twilight wednesday week weekday weekend weekly wintertime yearly
yesterday
""".split()

INTERNET_SOCIAL = """
account admin alert algorithm analytics anonymous app avatar banner
biodata block blog blogger bookmark broadcast browser buffer
catfish channel chat chatroom click cloud comment community
connection content cookie copyright crowdsource crowdfunding
cyberbullying darkweb data database deepfake delete developer
digital directory domain download ecommerce email embed emoji
encryption engage engagement engine explore extension fake
feed file filter firewall flame follow follower forum friend
gaming geolocation gif google hacker handle hashtag homepage host
html hyperlink identity image inbox influencer infrared internet
keyword landing layout like link live loading login logout
malware meme mention message metadata microblog mobile moderate
moderator monetize mute navigate netiquette network newsfeed
notification offline online optimize organic outreach page password
permalink phishing pin pixel platform playlist plugin podcast
popup portal post premium preview privacy private profile
promote promotion protocol public publish push rating react reach
reblog reboot recipe recommend redirect referral register repost
request resource retweet review robot rss scan screenshot scroll
search security selfie server share sidebar signup sitemap
smartphone social software spam sponsored startup status storage
stream streaming subscribe subscriber subscription surf sync tag
template text theme thread thumbnail timeline traffic trend
trending troll tweet unfollow unsubscribe update upload url
user username verification video view viral virtual virus vlog
webpage website widget wiki wireless
""".split()

COMPOUND_WORDS = """
aftereffect afterlife aftermarket aftershock afterthought airborne airbrush
aircraft airfield airflow airframe airline airliner airmail airplane airport
airship airstrike airtight airwave airway alongside anymore anyone
anyplace anything anytime anywhere applesauce arrowhead artwork
babysit backbone backbreaker backcountry backdrop backbone backdoor
backfield backfire background backhand backlash backlog backorder backpack
backpedal backrest backstage backstop backstory backstretch backstroke
backtrack backup backward backwater backyard ballgame ballpark ballplayer
ballpoint ballroom bankroll bandwidth barefoot bareback bargain
barnstorm barnyard baseball basketball bathrobe bathroom battleground
battlefield beachfront beachhead bearable bearskin bedclothes bedpost
bedroom bedsore bedspread bedtime beehive beforehand billboard birdsong
birthday birthplace birthrate blackberry blackbird blackboard blackmail
blackout blacktop blaze blindfold blindside bloodhound bloodline
bloodshed bloodshot bloodstream blueberry blueprint boardroom bodywork
bonfire bookcase bookkeeper bookmark bookshelf bootstrap borderline
boyfriend brainstorm breakdown breakfast breakneck breakthrough briefcase
broadcast brownstone brushstroke buckwheat buildup bulldozer bulletproof
bullpen bunkhouse burnout businessman butterscotch buttermilk bypass
cabinetmaker campfire campground campsite candlelight candlestick
cardholder cardboard carefree caretaker carjack carpet carpool cartwheel
caseload cashback catfish centerfold chainsaw chairlift chairman
charcoal checkerboard checkout checkpoint cheerleader cheeseburger
cheesecake chopstick churchyard clearinghouse clipboard clockwise
clockwork closeout cloudbreak cloverleaf coastguard cobblestone
cockroach coffeehouse commonplace commonweal cookbook copperhead copyright
cornfield cornerstone countdown counteract counterattack counterbalance
counterfeit counterpart courthouse courtyard cowherd cowgirl cowboy
crackdown craftsman crankshaft crossbar crossbow crossfire crossover
crossroad crosswalk cupcake cupboard cutback cutoff
daredevil darkroom database daughter daybreak daydream daylight
daytime deadline deadpan dealership deathtrap deckhand deerskin
dishwasher doorbell doorknob doorstep doorway double downbeat
downfall downgrade downhill download downpour downright downside
downsize downstairs downstream downtown downturn draftsman drawback
drawbridge dreamland dressmaker driftwood drivethrough driveway
dropout drumstick dugout dustpan
eardrum earphone earring earthquake earthworm eggplant eggshell
elsewhere enrollment entryway escapegoat everybody everyday
everyone everything everywhere evermore eyeball eyebrow eyecatcher
eyeglass eyelash eyelid eyepiece eyesight eyewitness
facedown faceguard facelift faceplate facetime fallback fallout
farmhouse farmland farmyard farsighted fastback fastball fastener
feedback filmmaker fingerprint fingertip firebrand firebrick
firecracker firefighter firehouse fireman fireplace firewood
fireworks firsthand fisherman flagship flashback flashlight flatbed
flatland fledgling flintlock floodgate floodlight floodplain
floorboard flowchart flyover flywheel folklore followup footbridge
footage footfall foothill foothold footlights footnote footpath
footprint footstep footwork forecast forefront foreground forehand
forehead foreman foremost forerunner foresee foresight forestall
forestry foretell forethought forgiveness forklift format forsake
fortnight framework freefall freelance freerange freestanding
freeway frostbite fullback fulltime fundraiser
gamekeeper gatekeeper gateway getaway ghostwriter girlfriend giveaway
gladiator glassware globetrotter goalkeeper goaltender goatherd
godchild godfather godmother goldfish goodwill grandchild granddaughter
grandfather grandmother grandparent grandson grasshopper grassland
graveyard greenhouse gridiron gridlock groundbreaker groundhog
groundskeeper groundwater groundwork grownup guesswork guideline
gunpoint gunshot
haircut hairpiece hairstyle halfback halftone halfway hallmark
hallway hamburger handbook handcraft handicraft handkerchief handlebar
handmade handout handrail handshake handspring handstand handwork
handwriting hardback hardball hardboard hardcover hardship hardware
hardwood hatchback headache headband headdress headfirst headhunter
headlamp headlight headline headquarter headroom headset heartache
heartbeat heartbreak heartburn heartfelt heartland heatstroke
hedgehog helicopter helpline hemlock henceforth herdsman hereafter
hereby herein hereof hereto herself highway hillside himself
hitchhike hoardfrost hogwash holder holdup homebound homebuilder
homecoming homeland homeless homemade homeowner homepage homeroom
homeschool homesick homestead hometown homework hookup horseback
horsefly horseplay horsepower horseshoe hotdog hotline household
housekeeper housewife housework
icebreaker icecap icecream icemaker incoming indoor infield infrared
infield inlaid inland inmate innermost input inside insight
installment integer interplay interview intro ironwork itself
jackknife jackrabbit jailbreak jawbone jokester joyride junkyard
keepsake keyboard keyhole keymaster keynote keypad keystone keyword
kickback kickoff kindergarten kingpin kneecap knockdown knockout
landlord landmark landscape landslide laptop lawsuit layoff layout
leapfrog letterhead lifeblood lifeboat lifeguard lifeline lifelong
lifestyle lifetime liftoff lighthearted lighthouse lightweight
likewise limestone lineup linkup livestock lockdown lockout locksmith
logbook lonesome longbow longhand longhorn longsighted longtime
lookout loudspeaker lovebird lunchroom
mainstream makeshift makeup manhandle mankind manmade manpower
marketplace marksman masterclass mastermind masterpiece matchmaker
meanwhile meatball meatloaf midair midday middleman midfield midnight
midpoint midrange midsize midstream midsummer midterm midtown midway
midweek midwest milepost milestone milkshake millstone minefield
moonlight moonshine moreover motherboard motherhood motorboat
motorhome motorcycle mouthpiece mudslide multimedia
namesake nearby necklace necktie network newcomer newfound newsletter
newsroom newspaper newswire nightclub nightfall nightgown nightmare
nighttime nobleman nonprofit nonsense northeast northwest notebook
noteworthy nowhere nuthouse
offshore offspring onlooker onset ongoing online onside onslaught
onward openhearted outbid outbreak outburst outcast outclass outcome
outcry outdated outdo outdoor outdoors outfield outfit outgoing
outgrow outgun outlast outlaw outline outlook outnumber outpace
outpatient outperform outplay outpost outrage outreach outright outrun
outscore outside outsider outskirts outsmart outsource outspoken
outstanding outwit overachieve overall overboard overcast overcome
overcrowded overdo overdone overdose overdraft overdrive overdue
overflow overgrown overhead overhear overjoy overlap overlook
overnight overpower overrate overreact overrun oversee overshadow
overshoot oversight oversized oversleep overspend overstep overtake
overtime overtone overturn overview overwhelm overwork
paintbrush pancake paperback paperweight paperwork parenthood
partnership passbook passport passthrough password pathway patchwork
pawnshop payback payday payload payoff payroll peacekeeping
peacemaker peanut penknife peppermint pickup pickpocket pillowcase
pinpoint pipeline pitchfork plainclothes playback playground playmate
playoff playroom playwright plywood pocketbook pocketknife podcast
polestar policeman policewoman ponytail poolside popcorn postcard
postmaster postpone pothole powerhouse praiseworthy printout
proofread pullback pullover pushover
quicksand
raindrop rainfall rainstorm rainwater racetrack railroad railway
rainbow raincoat rangebound ratepayer rattlesnake readout rebuild
rearrange rearview receipt rebuild recall reconstruct redhead redo
refurbish regardless regulator reinvent remainder remake remodel
roadblock roadmap roadside roadway robotics rollback rollout rollover
roommate rooftop roundabout roundup rowboat runaway rundown
runway
safeguard sailboat salesclerk salesman sandbox sandcastle sandpaper
sandstone sandstorm sawmill scapegoat scarecrow schoolboy schoolgirl
scoreboard scorecard screenplay screwdriver seacoast seafloor seafood
seagull seahorse sealant seaport seashore seasick seaside seaweed
seatbelt setback shareware sheepdog shellfish shipwreck shopkeeper
shoplift shoreline shortbread shortcake shortchange shortcoming
shortcut shortage shortfall shorthand shortsighted shortstop
showdown showroom shutdown sideburns sidekick sideline sideshow
sidetrack sidewalk sideways signpost silverware skateboard slapstick
sleepover slideshow slowdown smartphone smokescreen smokestack snapshot
snowball snowboard snowdrift snowfall snowflake snowman snowplow
snowshoe snowstorm somebody someday somehow someone someplace
something sometime somewhere songbird songwriter soundtrack southeast
southeast southwest spaceship spacecraft sparkplug speakeasy speedboat
speedometer spellcheck spillover spokesperson sportsman spotlight
springboard springtime stagecoach staircase stairway standby
standoff standout standpoint standstill starfish statehood steamboat
steamroll stepbrother stepdaughter stepfather stepmother stepsister
stepson stingray stockbroker stockholder stockpile stockroom stoplight
stopover stopwatch storefront storeroom storyline straightforward
strawberry streamline streetlight stronghold subcommittee subcontract
subculture subdivide submarine subplot subscribe subtext sunbathe
sunburn sundial sunflower sunlight sunray sunrise sunroof sunset
sunshine sunspot sunstroke supercharge superficial superhero
superhuman superimpose superman supermarket superpower supersonic
superstar surfboard surname swallowtail sweepstake sweetheart
switchboard swordfish
tabletop tailgate tailpipe takeoff takeover taxpayer teacup teammate
teamwork teaspoon teenager textbook themselves thereby therefore
thunderbolt thunderstorm tightrope timekeeper timeline timeout
timestamp timetable tiptoe toothbrush toothpaste topsoil touchback
touchdown touchstone trademark tradeoff trademark trademark
trainwreck treehouse troubleshoot troublemaker tryout tugboat
tumbleweed turnabout turnaround turnover turnpike turntable
typewriter
umbrella upbeat upcoming update upgrade uphill upload uppercase
uppercut uproar upstream uptown upturn upward upwind
viewfinder volleyball
waistcoat walkout walkway wallflower wallpaper warehouse warhead
warpath warship washboard washcloth washout washroom wasteland watchdog
watchmaker watchtower watercolor waterfall waterfront watermelon
waterpower waterproof watershed watertight waterway waveform waxwork
weatherman webmaster weekday weekend welfare wellbeing wheelbarrow
wheelchair whichever wholesale widespread wildfire wildlife willpower
windbreaker windfall windmill windowpane windshield windsock windstorm
wintergreen wintertime wishbone withdrawal withhold withstand woodchuck
woodland woodpecker woodwork worksheet worldwide workaround workbench
workforce workout workplace workspace workshop worldview worthwhile
wristwatch
yearbook
""".split()

ADDITIONAL_VERBS = """
abduct abstain accrue acquaint adjoin admonish adorn afflict aggravate
allot amass amble amputate annex appease apportion apprize arbitrate
ascertain assay attest augment avenge avert
backpedal badger baffle baptize beckon befall begrudge behead belittle
bemoan bequeath bereave beseech besiege bestow beware bewilder bewitch
blackmail blare blemish blindfold blister bludgeon bolster bombard
bother brandish broach buckle buffet bungle burglarize burnish
cajole capsize careen carouse catapult censure chafe chasten cherish
chide christen circumvent clamber clamor clutter coalesce coax coerce
coincide collide collude commend commemorate commiserate compile
conceal conceive condone confiscate congregate conjugate conjure
connive consecrate console conspire construe contaminate contemplate
contort contravene convene converge convoy convulse corrode covet
cringe crumble crumple culminate curtail
dangle dawdle dazzle debilitate decimate decompose decoy decry deepen
deflate deflect defraud demolish denounce deplete deplore designate
deter detonate devastate diagnose differentiate dilute diminish
discern disclose discredit disembark disfigure disillusion dismantle
dismay dispel dispense disperse displace dissipate distort divert
divulge dodge dote downplay dwindle
eavesdrop edify efface elapse elude emanate emancipate embark emboss
empathize encapsulate enchant encircle encompass encroach encrypt
endanger endear endeavor endow engrave engross enlighten enrage
enrich ensue entangle entice enumerate enunciate epitomize equate
eradicate erode escalate eschew evade evaporate evict exalt exasperate
excavate excerpt exclaim exclude exert exhale exhort exonerate expedite
expend extinguish extort extricate exude
fabricate falter fasten ferment feud fiddle flinch flourish foil
fondle forage forestall forfeit foster fracture fret fumble
galvanize garnish gawk glaze glimpse gloat gnaw goad gobble gorge
gouge graze grieve groan grovel grumble gulp guzzle
habituate haggle harden harness hasten hatch haunt heave heighten
hinder hoard holler hone horrify humiliate hurl hurtle
idolize ignite illuminate immobilize impair impale impeach impede
impersonate implore incapacitate incite infringe inhibit inscribe
instigate intercede intertwine intoxicate intrigue inundate invalidate
invigorate invoke irk
jabber jeopardize jostle jubilate jumble justify
keel kindle knead
lament languish latch lavish lessen lighten liken linger loathe
lubricate lunge lurch lurk
magnify malfunction manifest maneuver mar massacre meddle meditate
meander memorize menace mend mesmerize migrate mingle mislead mitigate
mobilize mock moisten mold mollify monopolize mourn muddle muffle
mumble muster mutilate muzzle mystify
nab nag negate nestle nibble nourish nudge nullify nurture
obfuscate oblige obscure obstruct ogle omit ooze orchestrate ordain
oust outlaw outrage outsmart outwit overhaul overlook overpower
override overwhelm
pacify pamper panic paralyze pardon partake patronize pawn peddle
penalize penetrate perch perplex persecute persevere pertain perturb
pervade petition petrify pilfer pillage pinpoint pioneer placate
plead pluck plummet plunder poise ponder pounce preclude preoccupy
presume prevail proclaim prod prolong promulgate propel prophesy
protrude provoke pry pummel punctuate purge purify
quash quell quench quibble
radiate raffle ramble ransack rattle ravage raze reaffirm realign
reassemble reassure rebuke recede reckon reclaim reconcile recount
rectify redeem redefine reek reformulate refrain refurbish refute
regain rekindle relegate relish reminisce renovate renounce repeal
repel replenish reprimand reproach repudiate resemble resent reside
retaliate retract retribute retrieve reunite revamp revere revive
revoke ridicule rivet roam rotate ruffle rumble rummage rupture
salvage sanction saturate savor scavenge scold scorn scour scramble
scrutinize scurry segregate seize sever shatter shimmer shrivel shrug
shudder shun signify simmer singe sizzle skimp skirmish slack slacken
slander slither snare snarl snatch sneer snicker snoop snuggle soar
soothe speculate splice splurge spoil sprawl sprinkle sprint squander
squash squeal squelch stagger stagnate stammer stampede startle
steer stimulate stir straddle strangle streamline strive stumble
stun subdue submerge subside subsidize substantiate suffice summon
supersede supplement suppress surmise surmount surpass swerve
tamper tantalize terminate terrify testify thicken throttle thwart
topple torment toil toss trample transcend transgress traverse
tremble trespass trickle trigger trudge truncate tumble twitch
uncover undercut underestimate undermine underscore undertake undo
unearth unfold unify unleash unlock unravel unseat unveil uphold
uplift uproot usurp
vacate validate vanquish veer ventilate verify vie vindicate violate
wager waive wane warrant waver waylay wean weave wedge whirl whisk
wield wilt wince wither wobble wrangle wrestle wring writhe
""".split()

ADDITIONAL_NOUNS = """
abacus abyss accessory accomplice accord acorn acre adhesive advent
adversary affinity agenda ailment aisle allegiance allotment almanac
amendment amenity ancestor anchor anecdote anguish annex anomaly
anthem antidote aperture apparatus appetite appliance apprentice
aquarium arcade archway arena armada armor aroma array arsenal artifact
ascent aspiration assault asylum attic audacity aura avenue avalanche
aversion awning axle
baboon bachelor backlog badge ballot bandage banjo banner banquet
barrage barricade basin battalion beacon bead beam bedlam benchmark
benefactor biography bladder blanket blaze blizzard blockade bloom
blueprint blunder bodyguard bolt bombshell bondage bonfire bonus
borough bounty bracelet breach brewery brigade brochure broker
bruise bubble buckle buffalo buffet bulge bulletin bumper bunch bunker
buoy bureau bureaucracy burial burst bust butcher
cabin cabinet cadet cafeteria cage cairn calamity caliber camouflage
campaign canal canopy cape capsule captain captive caravan carbon
cargo carnival carpet cartridge cascade cashier casket casualty catalog
cathedral cavalry cavern cavity census ceremony chancellor chandelier
chapel chaplain charm charter chasm cherub chisel chronicle chunk cipher
citadel civilian clamp clan clarity clash clause clearance clergy clerk
cliff closure cluster clutch coil coincidence collaboration collar
colony combustion comma commander commando commemoration commentary
commerce commission commodity communique companion compass compendium
complexion compliment compromise compulsion comrade concession
concord confetti confession confiscation congregation conscience
conscription consensus conspiracy constable constellation consulate
contention continent contingent contractor contrivance conundrum
convoy corridor cortex counsel countenance covenant creek crevice
crewman crimson criterion critique crossbow crucible crusade crust
crypt cuisine culprit cupola curator curb cushion custard custodian
cylinder
dagger dam dame dashboard daydream deadlock dealer debris debtor
deceit decree dedication default defect deficit deity delicacy
demolition demon denial denizen denomination depot deputy descent
despair detachment detention deterrent devastation dialect dilemma
dimension dinosaur discourse discrepancy disorder dispatch dispatcher
disposition distinction dividend divinity doctrine dominion donation
dormant downfall dowry dread drizzle drought duel dungeon duration
dweller dynasty
eagle earring easel eclipse edict edifice elbow elder elegy elite
embargo emblem embryo emission emperor emphasis empress enclosure
endeavor endorsement enigma ensemble enterprise enthusiasm entity
envelope envoy epoch equator equilibrium errand escapade escort essence
estate esteem etiquette evacuation excerpt exclamation exodus
expedition expenditure exploit exposition extravagance
fable fabric faction faculty fallacy famine fang fare fathom fauna
feast feather feeble fellowship felon festivity feud fiber fiasco
fiction figurine filament filler finery fixture flair flask flaw
flicker flock flora flourish flutter flux foliage footprint forecast
foreman forerunner forge formula forte fortress fossil fracture
fragment franchise fraud fresco friction frontier fugitive
gadget gallop garland garrison garrison gazette gem genius genre
gesture ghetto glacier glamour glen glimmer glitch glimpse globe gloom
glutton goblet gospel granite gratitude gravity grief grin grit groove
grove grudge guardian guerrilla guild guise gulf gust gutter
habitat halo hamlet handout harness haven havoc hazard heyday hinge
hobbit hoist homestead horizon hormone hornet hospice hospitality
hostage hurricane hymn hypocrisy
iceberg ideology idol ignition illusion immersion immunity impasse
imperative implement imposition impulse incentive inception incision
inclination increment indictment indulgence infantry influx ingredient
inheritance inlet innovation inquest inscription insight inspector
instinct institution intake intellect intercept interim interior
intrigue inventory invoice irrigation island
janitor javelin jeopardy jester jingle jostle jubilee juncture jungle
jurisdiction jury
kaleidoscope kayak kernel keynote kidnap kingdom kinship knack
labyrinth lagoon landmark landowner landslide latitude laundry lawsuit
leaflet league legend legislature leniency leverage levy lexicon
liberty lieutenant ligament lighthouse limousine lineage linen
livelihood locker locomotive lofty logistics longevity lottery lull
lumber luncheon luster luxury lynching
madness magnitude maiden malice mandate maneuver manifesto mansion
marathon marble mariner marketplace marquee massacre maternity matrix
mayhem mayor mechanism medallion memoir memoir memento membrane
merchandise merchant mercy merger metaphor metropolis microphone
midpoint migration militia millennium minaret miniature ministry
minority miracle mirage mischief missile missionary mitten moat
mockup module momentum monastery monopoly monument morale moratorium
morsel mortgage mosaic mosque mound mourning multitude municipality
mural murmur mustache mutation myth
narrator nausea necessity negligence nemesis nephew nerve nest
newsletter niche nightfall nobility nomination nomad nook nostalgia
notion novelty nucleus nuisance nursery nutrition
oasis objection obituary obsession obstacle occasion octave odyssey
offense omen onset opponent oracle orchard ordeal ordinance ornament
orphan onslaught outage outcome outfit outlaw outlook output outrage
outskirt oversight
pageant palette pamphlet pandemic panorama parachute paradise paradox
parish parliament parody pastime patent patriarch patron pavilion
penal peninsula pension perception peril perpetrator personnel
petition phantom phenomenon pillar pilgrimage pinnacle pioneer
placebo plague plea plaza pledge plight ploy plumbing plunder
pneumonia poacher podium poise polygon populace portfolio potion
pottery pounce predecessor predicament preface prejudice premier
premium premonition prescription presidency prestige prevalence prey
primer privacy privilege probe proclamation prodigy profession
projection prominence prophecy proportion proprietor prose prospect
prosperity protocol provocation prowess provision psyche publicity
pulse purchase pyramid
quarantine quest queue quota
radius rally ranch ransom rapport rascal ratio rationale realm
rebellion recipe recipient recluse reconnaissance recruit reel refuge
regiment rehearsal relic reluctance remainder remedy remnant
renaissance rendition renown repercussion replica reproach republic
repute requisite reservoir residue resilience resolution resort
resource respite restraint resume revelation revenue reverie revision
revival rhetoric rider ritual rivalry rivet robust roster rotation
routine royalty rubble rumor rupture
sabbath sacrament sacrifice sage salvation sampler sanctuary sanction
satchel satellite satire scandal scaffold scenery schema scheme
schooner scourge scripture scrutiny sculpture seminary senate
sensation sentiment sequel sergeant sermon serpent session setback
shatter shelter siege silhouette simplicity skeleton skeptic slab
slogan slope slum snarl snippet sociology socket solace solidarity
solitude sovereignty specimen spectacle speculator sphere spire
splendor sponsor spree squadron stagnation stake stalemate stamina
stampede stance staple stature statute stimulus stipulation stockpile
strategy strife stronghold structure submission substance successor
summit summit superstition supplement supremacy surplus surveillance
suspension suspicion swamp swarm syllable symbol symmetry symposium
symptom syndrome synopsis
tabernacle tact tactics talisman tangent tariff tavern temperament
temple tempo tenant tenure terrain terrain testament testament
testimony texture threshold thrill threshold throne tidal timber
titan tolerance toll torrent totalitarian tournament toxin tradition
trait trajectory transcript transmission trauma treasure treaty
tremor tribunal tribute triumph trophy troupe trustee tumult tunnel
turbulence turnout tutor tycoon tyranny
ultimatum umpire underdog undertaker undertaking unemployment
unification unity universe upheaval uprising uproar upside uranium
utterance
vacuum vagrant valor vandal vanguard variable vault vegetation
velocity vengeance venue verdict verge verse vessel veteran veto
vicinity victory vigor villain vintage violation virtue visa
visionary vision void volume vow voyage
waiver warehouse warden warfare warrant warrant warrior watershed
welfare wilderness windfall wisdom withdrawal wizard workforce
wreath wreckage wrestle
yacht yeoman yield
zealot zenith
""".split()

ADDITIONAL_ADJECTIVES = """
abiding abject abominable abrasive abrupt absurd abundant accessible
acrid adamant adept adjacent adorable adverse affable affluent
aghast agile ailing ajar akin alight aloof altruistic ambient
ambivalent amiable ample angular anomalous antiquated appalling
appealing appetizing approachable apt ardent arduous arid artful
astounding astute atrocious audacious austere authentic avid
balmy baneful barbaric barren bawdy befitting belated believable
benevolent benign bewildered billowing bland blaring blatant bleak
blissful bloated bogus boisterous brainy brash brisk bristly brittle
bumpy buoyant burdensome burly bustling
callous candid capable capricious caring catchy caustic celestial
cerebral charitable cheeky chronic civic clandestine classy clumsy
coarse colossal combustible comical commensurate commonplace
compatible complex compliant composed conceivable concrete congenial
conniving consecutive conspicuous contentious conventional copious
cordial covert cozy crafty cranky creepy crisp crude cumbersome
cunning curvy cynical
dainty damp dapper daring daunting dazzling decrepit defiant delectable
delightful dense derogatory desolate detached devout dexterous
dilapidated diminutive dire discernible discrete dismal disposable
distinct docile dormant downright drab dreary dubious dull dumpy
duplex durable dusty dutiful
effervescent elaborate elated elder elicit elite elongated emaciated
eminent emphatic enchanting endearing engrossing enigmatic enormous
enthralling enviable equitable erratic essential estranged ethereal
euphoric evasive eventual evident exasperated excessive exhilarating
exotic expedient explicit exquisite extravagant exuberant
fabled facile faint fallacious fanciful farsighted fastidious fathomless
feeble feisty felicitous fervent festive fetching fickle fierce
finicky flagrant flamboyant flashy fleeting flimsy floppy florid fluffy
foamy foggy foolproof forbidding foreboding formidable forthright
fragile frail fraudulent frenzied frigid frothy frugal furtive futile
gallant garish gaudy gaunt genial ghastly ghoulish giddy gigantic
gimmicky glacial gleaming glib glittering gloomy glossy gnarly
gorgeous grandiose granular grateful gregarious grim grimy gripping
gritty groovy grotesque grubby grueling gruesome grumpy guarded gusty
gutsy
haggard haphazard hapless harrowing hassle haughty hearty hefty
heinous hectic hideous hilly hoarse hollow homely honourable hopeful
horrendous horrific hostile hubbub humdrum humiliating humorless humid
hushed
icy illicit illustrious immaculate immense imminent immoral immovable
impeccable imperial impetuous impish implausible imposing impregnable
impromptu imprudent impulsive incandescent incisive inconceivable
indelible indicative indistinct indolent indomitable industrious inept
inexorable infantile infinite infuriating ingenious inherent innocuous
inquisitive insidious insipid insolent insular intact intangible
intellectual intermittent intimate intrepid intricate intrusive
invaluable invasive irate irksome irrefutable irrelevant irreparable
irresistible irritable
jaunty jazzy jealous jittery jocund jolly jubilant judicious jumbled
keen
lackluster languid lavish lenient lethal lighthearted limber listless
livid lonesome lopsided lucid luminous lush luxurious
macabre magnificent majestic malevolent maniacal manifest manual
marginal marvelous mediocre melodious menacing meticulous mighty
miraculous misty moderate moldy momentary monotonous monstrous moody
morbid motionless mountainous mournful muddled mundane murky musty
myriad
naive nasty naughty negligible nervous nimble nocturnal nondescript
nontoxic nostalgic noteworthy noxious numerous nutritious
obedient objective obnoxious obscene observable obsolete obstinate
obtrusive odd offbeat ominous opaque oppressive ornamental ornate
ostentatious outlandish outrageous
palatable panoramic paramount pathetic peculiar peerless pending
perceptive perilous peripheral perpetual perplexing persistent
pertinent petite phenomenal placid plausible plentiful poignant polished
porous potent precarious precious predictable predominant preliminary
preposterous prestigious presumptuous prevalent primitive pristine
prodigious productive profane profitable profound profuse prolific
prominent promising prompt prone propitious prosperous provocative
prudent pungent puny putrid puzzling
quaint quarrelsome queasy quizzical
rabid radiant rambunctious rampant random raucous ravenous receptive
reckless recurrent redundant relentless remarkable reminiscent renowned
reprehensible repulsive resilient resonant respectable restrained
reverberant ridiculous righteous rigorous riveting robust roomy rotund
rowdy rubbery rudimentary ruffled rugged rustic ruthless
saccharine sacrosanct scenic scenic scintillating scrawny scrumptious
scrupulous sculpted seamless secluded sedentary seedy seismic sensible
sequestered serendipitous serpentine shabby shady shallow shameless
shapely sheer shimmering shrewd skeptical skeletal skillful slender
slick sluggish smug snarky snobbish snug solemn somber sophisticated
sordid specious spectacular spherical spirited splendid sporadic
stagnant stark steadfast stealthy stellar stiff stoic stolid stout
strident striking stringent stupendous subdued sublime subordinate
substantial subtle succinct sullen sumptuous superb superficial
supple surly surplus suspicious svelte swanky sympathetic
tacit tainted tangible tantalizing tedious tenacious tenuous tepid
terrific terse theatrical therapeutic thorny thorough thunderous tidy
tiresome titanic tolerant topical torrid treacherous treble tremendous
trivial tropical tumultuous
ubiquitous ultimate umbral unambiguous unbearable uncanny
unconventional undeniable understated unfathomable unforgettable
ungainly unilateral unique universal unkempt unorthodox unparalleled
unprecedented unsightly unspeakable unspoiled unsteady untimely
unwieldy upbeat uppity upright uproarious useful utilitarian utmost
utter
vague valiant valid vapid varied vehement venerable verbose verdant
versatile vicious vigilant vigorous vindictive visceral vivacious
vivid vociferous volatile voluminous voracious vulnerable
wanton warranted wasteful watchful watertight weary weathered weird
whimsical wholesome wicked willful wily windy winsome wiry witty
wobbly woeful worldly worthy wretched
zealous zesty
""".split()

FINAL_BATCH = """
absinthe accordion acorn acrobat adhesive admiral adverb aerobics agate
airlock allspice almanac altimeter alto amphibian amplifier amputee anemone
anklet annex antler apiary apothecary appaloosa applique aquamarine arboretum
armadillo armband arpeggio arrowhead artifact artisan ashcan asphalt atoll
aubergine auburn audiology awl awning axle
babysitter badlands baguette balalaika ballerina bamboo bandage bandanna
banjo banyan bargeboard baritone barley barnacle barracuda barrette
basilica baton battleship bazooka beachcomber beadwork beanpole bedroll
beechnut bellboy bellhop bellows benchmark bequest bestseller bicuspid
billboard billfold binoculars birdbath birdcage birdseed birthstone biscotti
bisque bistro blackberry blackbird blackboard blackjack blackthorn blanch
blancmange blazer blender bloodhound bluebell bluebird boardroom bobbin
bobcat bocce bodysuit bonbon bondsman bonefish bonfire bookmark bookworm
boomerang bootcamp bottleneck bouillon bouquet bowstring boxcar bracken
braggart bratwurst breadcrumb breadwinner breakwater bridgework briefcase
brimstone bristle broadcloth broadcast broadsword brochure brontosaurus
brownstone brushfire brushstroke buccaneer buckboard buckskin buckwheat
bufferzone bugle bulldozer bulletin bullfinch bullpen bullring bumblebee
bunkbed burlap bushcraft buttercup buttermilk butterscotch byproduct
cabochon caboose cactus cairn cakewalk calfskin calliope camshaft candlewick
canister cannibal canteen capstone carabiner caravel cardstock caribou
carjacker carousel carpentry cartographer cartwheel casebook cashew
cashmere cassette castaway catamaran catchword catfish catnap cattail
cauliflower cavalcade cavernous cayenne cellophane centaur centerpiece
chainlink chameleon chamomile chandelier chaperone cheekbone cheetah
chestboard chieftain chimpanzee chinchilla chiropractor chitchat chloroform
chophouse chopstick chromosome chrysanthemum chuckwagon chutney cider
cinderblock cinnamon clambake clamshell clapboard clearance clipboard
cliffhanger clingwrap clockmaker clothespin cloverleaf coachwork coattail
cobalt cobblestone cockleshell cocklebur cockpit coconut codebook codger
coffeecake coffeepot cogwheel colander collage collarbone columbine combustor
commemorative commonality compendium compost concertina concourse condiment
confectionery conifer conquistador conservancy constellation constellation
cookbook copperplate copybook copyrighter coracle cordwood corkboard corkscrew
cornerstone cornucopia corsage cottonwood countdown counterpane counterweight
countryside courthouse courtship cowhide coxswain crabapple crackerjack
craftwork crankshaft crayfish crescent crewcut crockpot crossbow crosshatch
crouton crowbar crucifix cuckoo cucumber cufflink cupcake cupola curlicue
curtainrod cutlery cuttlefish
daffodil daggerboard dalmatian damselfly dandelion darkroom dashboard
datebook daylily deadbolt deadwood deckhand deerskin delftware demitasse
dervish dewclaw dewdrop diaphragm diecast dinghy dinosaur discjockey
dishcloth dishrag ditchwater dockhands dogtooth dollhouse doorjamb doorknocker
doorstop dormouse dosimeter doubleheader doughnut dovetail downstairs
dragonboat dragonfly drawbridge drawstring dreadnought dreamcatcher drivetrain
duckbill dumpling dunghill dustbin
eardrum earthenware earthworm easternwood eggcup elderberry embankment
embroidery endcap entryway epaulet espresso euglena eventide everything
excavator eyecup eyedrop eyepatch eyeliner
faceplate facecloth fairground farmstead faucethead fiddlestick figurehead
filament filigree fingertip finstock fireboat firebrand firecracker firetruck
flagpole flagship flameproof flatware flaxseed flintlock flipchart floormat
flowerbed flywheel foghorn foodstuff footbridge footlocker forkfist
fountainhead foxhound foxtail freethinker fretwork frontrunner frostbite
fruitcake funnelweb
gallstone gamebird gamesmanship gardenpath gaslight gatehouse gearbox
gearshift gemstone gentlewoman gingerbread gingerroot gladiolus glassblower
glassware globefish goalkeeper goatskin godparent goldfinch gondolier
gooseberry grapevine gravedigger greenhouse greenroom grindstone groomsman
groundhog grubstake guidepost gumball gumshoe gunboat gunmetal
hailstone hairbrush hairclip hairpiece hairspring halfback hallmark
hammerhead hammock handbarrow handbill handiwork handlebars handmaid
handspring hardboard hardtack harpoon harpsichord haversack haymaker
hayloft haystack headcount headfirst headlamp headmaster headquarters
hearthstone heartstring heatstroke hemlock henhouse henpeck herdsman
herringbone hibiscus highchair highland hobgoblin hoedown hogback homesteader
honeybee honeycomb honeymooner hookworm hoopla hopscotch hornblende
hornpipe horseback horsehair horseradish hosedown hotcake hotshot
houseboat housecoat housefly housemother housewarming humpback
""".split()

ADDITIONAL_VERBS2 = """
abase abash abbreviate abhor abjure abnegate abort abridge abscond absolve
abstain absorb accede accentuate acclimatize accost accredit accumulate
acquiesce acquit actualize admonish adulterate affix aggrandize agglomerate
alienate alleviate amalgamate amass ambush amortize annex annihilate annotate
anoint appease append apportion apprize appropriate arbitrate articulate
ascribe assail assimilate assuage attenuate attest aver avow
backslide badmouth banish barter befriend begrudge beleaguer belittle bequeath
berate bereave besiege bestride betroth bilk bisect blacken blaspheme
blemish blister blockade bludgeon bolster bootstrap botch brace brandish
broach browbeat brutalize burnish buttress
calibrate canvass capitulate captivate careen categorize censure centralize
chastise christen circumscribe clamp clarion clobber coagulate cohabit
commandeer commemorate commingle commute compel compile complement complicate
concatenate concede conciliate confiscate conflate congregate conjecture
consecrate consolidate conspire constrict construe consummate contort
contravene convalesce converge convoke corrode corroborate countermand
counterbalance crumple crystallize cultivate curb curtail customize
dangle debase debrief debunk decapitate decimate decipher declassify decolonize
decommission decontaminate decriminalize decrypt deduct defame default deflate
defraud degrade delegate delineate deluge demagogue demean demobilize
democratize demonize demoralize demote demystify denationalize denigrate
denominate denounce denude depersonalize depopulate deport deprecate
deregulate desecrate desensitize designate despise despoil destabilize detach
detain detonate detour devalue deviate digitize dilute diminish disallow
disambiguate disassemble disassociate disclaim disconnect discountenance
discriminate disembark disentangle disfigure disgorge disillusion disinfect
disintegrate disjoint dislodge dismantle disorient disown dispatch disperse
displace dissect disseminate dissolve dissuade distill distort divert divest
disburse domesticate downgrade downsize dramatize dredge duplicate
earmark edify effectuate eject elicit elongate emaciate emancipate embark
embattle embed embellish embezzle embody embolden embrace embroil empathize
empower emulate enact encapsulate enchant encircle encompass encroach encrypt
endanger endorse engender engrave engross enmesh entail entangle enthuse
entrench enumerate enumerate envision equalize equate equilibrate eradicate
escalate espouse estrange exacerbate exasperate excavate excommunicate
excoriate exemplify exert exhaust exhilarate exhume exonerate expatriate
expedite expiate exploit expound expropriate expunge exterminate extol
extort extract extricate exult
facilitate fathom finagle finalize flagellate flounder fluctuate formalize
formulate forsake fortify franchise fraternize freeload fulminate
galvanize genuflect germinate gerrymander gesticulate globalize grapple
harmonize harness harry heckle heighten hijack homogenize hoodwink
hospitalize humanize humble hybridize hypothecate hypothesize
identify idolize illuminate imbibe imbue immerse immobilize immortalize
impale impede impel impersonate implant implicate importune impound
improvise impugn inaugurate incapacitate incentivize incinerate incite
incorporate incriminate inculcate indemnify indoctrinate industrialize
infantilize infiltrate ingratiate inoculate inscribe institutionalize
instigate integrate interpolate interpose interrelate interrogate intertwine
intoxicate introspect inundate invalidate invigorate irradiate itemize
juxtapose
kindle
lampoon legislate legitimize liberate liquefy liquidate lobby
macerate magnetize malign manipulate mangle marginalize marinate mastermind
meander mechanize memorialize merchandise metamorphose micromanage minimize
misallocate miscalculate mischaracterize misconstrue mismanage misinform
misinterpret misrepresent mispronounce misspell mobilize modernize mollify
monopolize moralize mortify mothball municipalize muzzle mystify
nationalize necessitate negate neutralize nominate normalize notarize
obliterate obstruct officiate operationalize orchestrate ostracize outmaneuver
outperform overhear overrule oversimplify
parachute parallelize paralyze paraphrase pardon patronize penalize
percolate perpetrate perpetuate personalize personify petrify philosophize
plagiarize pluralize polarize politicize popularize posterize postulate
predetermine predominate prefabricate presuppose prioritize privatize
problematize procrastinate professionalize proletarianize prolong promulgate
propagandize propagate prophesy proselytize pulverize punctuate putrefy
quarantine
radicalize randomize rationalize reanimate reassemble reassess recalibrate
recapitulate reciprocate reclassify reconfigure reconstitute reconvene
recriminalize redistribute reformulate regenerate rehabilitate reincorporate
reinstate reinterpret relegate remediate remilitarize remodel remunerate
renationalize renegotiate repopulate repossess reprioritize resensitize
restructure resurface resuscitate rethink revolutionize
sabotage sacrifice salivate sanctify satirize scandalize scrutinize secularize
sensationalize sentimentalize serialize sever shanghai shelve shepherd
signpost simplify slaughter socialize solemnize solidify specialize stabilize
standardize sterilize stigmatize stipulate strategize streamline strengthen
subjugate sublimate subordinate subsidize substantiate substitute
suburbanize suffocate summarize supplement surrender surveil symbolize
sympathize synchronize systematize
tantalize temporize terrorize theorize topple totalize transcend transfigure
transgress traumatize trivialize troubleshoot
underestimate undermine undertake undulate universalize unravel
validate vandalize venerate verbalize verify victimize vilify vindicate
visualize vocalize volunteer vulcanize
waterproof weaponize winterize
""".split()

ADDITIONAL_NOUNS2 = """
abacus abbot abbreviation abduction abolition abomination abruption
abstention abstinence abstraction abundance accordion accusation
acknowledgement acquaintance acquisition adage adaptation adequacy
adherent adjective adjournment admirer adolescence adversary
advertisement affectation affiliation affliction aftereffect
afterglow aftermath aggregate aggravation agony ailment airstrip
alderman algorithm alienation alignment allotment altercation
altitude aluminium amendment amusement ancestry animosity announcer
antagonism antecedent antidote aperture apology apparatus appendage
appetizer appliance aptitude aqueduct arbitration armament
arrangement artisan assailant assassination assemblage assertion
assignee assortment asterisk atrocity attendant attribute audacity
auditorium aura authorization autobiography avalanche avoidance
backdrop backyard bailiff ballad balm bandwagon banishment banner
barbarian bargain barge barrage barrister bastion battalion beacon
bearings beautician beginner behemoth bellhop benchmark benefaction
bicep biographer blackguard blackout blender blemish blessing
blindfold blizzard blockbuster bloodbath bluegrass blueprint boatswain
bodywork bolster bombardment bookmark boogeyman borderline boulevard
bowstring bracelet breakdown bribery briefing brigand brochure bronze
bulkhead bystander
cabal cadaver calibration campfire camper canteen capability caption
captivity caravan carbine cardigan caretaker cartoonist catastrophe
cauliflower cavalier caveat celebrity censorship centennial centimeter
certificate championship chapel charitable checkup chronicle
circumference circumstance civilization claimant clarinet
claustrophobia clergy clergyman clientele climber clipboard clique
clubhouse clutter coalition coastguard cobblestone cockade coincidence
combustion comedian commandment commentary commissioner committee
communicator commuter companion compassion competitor compilation
complement compliance complication compliment compulsion concession
concoction condolence confederation confessional configuration
confirmation confiscation confrontation congregation congressman
conjecture connoisseur consciousness conscription consecration
constellation consternation constituency constitution consultation
contemplation contentment contraction contradiction conveyance
conviction coordination coronation corporal corpse counterpart
countryside courtier courtship cowardice cowgirl credential
criticism culmination cutback
daydream daydreamer debacle debutante decency decor decoration
defection defendant defiance degradation delegation delineation
delinquent delimiter denomination deployment depravity depreciation
deprivation derivation designation desolation deterioration deviation
devotee dialogue dictator dilemma dimensionality diplomat directory
disability disadvantage disappearance disclaimer discomfort disclosure
discontinuation discouragement discretion disfigurement dishonor
disillusionment dismissal disobedience disparity dispensation
displacement disposition disqualification dissatisfaction dissidence
dissolution distillation distinction distortion disturbance dividend
documentation dollop dominion dormitory downfall downturn draftsman
dramatization drawback driveway drumbeat duality dungeon duplicity
duplication
eagerness earthquake easement easychair ecosystem editor effacement
efficiency eggnog elaborate electorate elimination embankment
embezzlement embodiment emigrant emission emigration emphasis
emplacement empowerment encounter encouragement endorsement
enforcement engagement engulfment enjoyment enlightenment enrollment
entanglement entertainment enthusiasm entitlement entrepreneur
enumeration envoy epitome equilibrium equivalent escalator estrangement
evaporation eventuality eviction evolution exaggeration examination
excavation exception exclamation exclusion exemplar exemption exertion
exhaustion exhibition exhilaration expedition expenditure expertise
exploitation exploration exposure expropriation extinction
extradition extravagance extremist eyewitness
fabrication facet facilitator faculty falconer fallacy familiarity
fascination fatality favoritism feathering fellowship feudalism
fiancee figurehead fishmonger flagship fixation flourish flyer
footbridge foothold footrest foresight forgiveness formality
fortification foundling fragmentation franchise fraternity
freeholder fulfillment fundamentalist furnishing
gallantry gameplan garnishment genealogy generalization gestation
glassmaker globetrotter goalkeeper godparent goldsmith goodbye
governance grandchild grandparent gratification gravity greengrocer
greetings grievance groundwork guardianship guideline gunslinger
gymnasium
habitation handicap handler hangover harassment hardship harmonics
headquarters heartbeat heirloom hemisphere heyday hideaway hindrance
historian holdup homesickness honeymoon hospitalization household
humiliation humility
icebreaker idealism ignition illumination illusion imagination
immigration imperfection implementation imposition imprint
improvisation inauguration incantation incompetence increment
independence indication indictment indignation individuality
industrialist inequality inferiority infirmity inflammation
infrastructure infringement inheritance injunction innovation
inquisition inscription insecurity insertion insignia inspiration
installation institution instrumentation insulation integration
interception interference intermediary intermission interpretation
intersection intimidation introduction introspection intuition
invalidation investigation investor invocation involvement isolation
itinerary
journalism juncture jurisdiction jurisprudence justification juvenile
keystroke kinsman
laborer laceration lamentation landlady landmark landscaping
lawbreaker leadership legislature liaison lieutenant likelihood
limitation listener litigation livelihood loneliness lumberjack
machination magistrate magnification mainstream malfunction
manifestation manipulation manufacturer marketplace matchbox
mathematics mechanism membership memorandum merchandise metabolism
methodology middleman migration millionaire misconception misbehavior
misdemeanor misfortune misinformation missionary moderation
modification monarchy monopolist motivation mountaineer municipality
""".split()

REAL_ESTATE_HOME = """
acre acreage adjustable affordability affordable amortize annex appraisal
appraiser appreciate appreciation asking assessment assessor attic
basement bathroom bedroom bidding birdhouse blight blueprint boundary
brokerage brownstone bungalow buyer cabinetry caulk ceiling cement
chimney cladding closing clutter codeviolation colonial commission
community comparable condominium conservation construction contractor
conventional conveyance cooperative counteroffer countertop covenant
craftsman crawlspace cul curb decommission decorator deed default
delinquent demolition den deposit depreciation detached developer
disclosure disposition distressed documentary dormitory down drainage
driveway duplex dwelling easement efficiency elevator encumbrance
energy equity escrow estate eviction excavate exterior facade faucet
federal fence finalize financing fireplace fixture flood flooring
floorplan flush foliage forbearance foreclosure foundation foyer
freehold frontage furnace gable garage garden gazebo granite
greenhouse gutter handyman hardwood heating heritage historic homebuyer
homeowner homestead housing improvement insulation interest interior
investment irrigation kitchen lake lakefront land landscaping
landlord laundry lease lender lending lien listing livable loan
location lockbox loft lot luxury maintenance mansion market
merchandise modular molding mortgage mortgagee mortgagor move
neighborhood occupancy oceanfront offmarket openhouse option ordinance
origination outbuilding outlook overgrown owner painting pantry parcel
patio penthouse permit pex pier plaster plat playground plot plumbing
porch preapproval prequalify prime probate property prorate purchase
railing ranch rancher realtor realty rebuild refinance remodel
remodeling renovation rent rental repair repossess resale residence
residential resurfacing retail retaining rezoning ridge roof roofing
roommate rural rustic sale salesperson sanitary sash septic settlement
sewage sewer siding sink slab soffit solar spacious spec split
sprinkler squat stain staging staircase stairway stoop storage
subcontract subdivision sublease suburb suburban sunroom survey
surveyor swimming terrace tile title topsoil townhome townhouse tract
transition trim triplex trust tudor turnkey underscore undeveloped
unit upgrade urban utilities utility vacancy vacant valuation value
vent ventilation veranda versatile vestibule Victorian vintage vinyl
walkthrough wallpaper warehouse warranty waterfront weatherize
wholesale window woodwork wraparound yard zoning
""".split()

ENVIRONMENT_ENERGY = """
absorb acidification activist aerosol afforestation agenda airborne
alternative amendment aquifer atmosphere atmospheric biodegradable
biodiversity bioenergy biofuel biomass biome biosphere biotech
breeding byproduct canopy capacity carbon carbondioxide capture
catalyst catastrophe cellulose charcoal circulation cleanup climate
climatology coal cogeneration combustion compost composting
condensation conifer conservation conserve contaminate contaminant
coral corridor cropland cultivate current cycle dam ddt decommission
decarbonize decontaminate decompose deforestation degradation
deplete desalination desertification deteriorate diesel discharge
disposal dispose drain drainage drought dry dumpsite dust
earthquake ecofriendly ecological ecology ecosystem effluent
emission emit endangered endemic endothermic energy enrichment
environment environmental environmentalism environmentalist enzyme
epidemic erosion ethanol eutrophication evaporation evolution
excavate exhaust exponential exposure extinction extreme
factory fallout fauna feedstock fertilizer filtration fishery fission
flora floodplain flow fluorescent flux footprint forestation forest
forestry formaldehyde fossil fragmentation freshwater fuel fungicide
fusion geothermal glacier global globally gradient grassland
greenfield greenhouse groundwater growth habitat harvest hazardous
heatwave herbicide heritage humidity hurricane hybrid hydration
hydro hydrocarbon hydroelectric hydrogen hydrological hydrology
hydropower ice icesheet ignite impact impermeable incinerate
incinerator indigenous industrial industrialize infrared insecticide
installation insulate insulation intake intensity intergovernmental
invasive irradiation irrigate irrigation
kelp keystone
lagoon landfill landscape latitude leach lifecycle limestone
lithium livestock logging longterm
magnitude mangrove marine marsh meadow megawatt meltdown mercury
methane microplastic migration mineral mitigation moisture monsoon
monoculture mountaintop
native nitrogen nocturnal nontoxic nuclear nutrient
ocean offshore offset oilspill organic overexploit overfishing
overgrazing ozone
particulate pathway peat permafrost pesticide petrochemical petroleum
photosynthesis photovoltaic phytoplankton plankton plantation
plastic plume poach poison pollinate pollutant pollute pollution
population potable precipitation predator preservation preserve
pristine procurement production propane protective protocol purify
purification
quarry quota radiation radioactive radon rainforest ranching
reclaim reclamation recycle recyclable reforestation refuge
regenerate regulate regulation rehabilitate rehabilitate remediation
remediate remote renewable reservoir residue resilience resilient
resource restore restructure reuse reusable revegetate rewild riparian
runoff
saline saltwater sandstorm sanitary savanna scarcity scenic
sediment sequester sequestration sewage shade shelter shrubland silt
smog snowmelt solar sourcing species stewardship stormwater
stratosphere stream subalpine subsurface sulfur surge sustainable
sustainability swamp symbiosis
tailings temperate thermal thermoelectric thinning tidal timber
topography topsoil tornado toxicity toxin transition tree tributary
tropical tropics turbine tundra typhoon
ultraviolet unleaded upstream urbanization
vegetation volcanic volcano volatile vulnerability
waste wasteland wastewater watershed wave weather weathering
wetland wildfire wildlife wind windmill windfarm woodlands
xeriscape
yield
zero zone
""".split()

PHILOSOPHY_RELIGION = """
absolution abstract aesthetics afterlife agnostic agnosticism alchemy
allegory altruism anarchism angel animism apocalypse apostle
archetype ascetic atheism atheist atonement authenticity autonomy
baptism beatitude belief benevolence bible biblical bishop blasphemy
bless blessing bodhi bodhisattva brahma buddha buddhism buddhist
calvinist canon cardinal catechism catholic causality celibacy chakra
chapel chaplain charity christ christening christian christianity
christmas church clergy clerical commandment communion compassion
confession confucian confucianism congregation conscience conscious
consciousness consecration contemplation conversion convent
cosmology covenant creation creed crucifix crucifixion crusade
cult deacon deity denomination determinism devotion dharma dialectic
diocese disciple discipline divination divine divinity doctrine dogma
dualism
ecclesiastical ecumenical eden edification egalitarian emanation
embodiment emancipation empiricism enlightenment epiphany
epistemology eschatology essentialist eternal eternity ethical
ethics eucharist evangelical evangelist evil evolution exegesis
existential existentialism exorcism
faith fatalism fasting fate fellowship feminist fetish forgiveness
foundational freethinker fundamentalism fundamentalist
genesis gnostic gnosis god goddess gospel grace
hagiography halal hajj heaven hebrew hedonism hell heresy heretic
hermeneutics hierarchy hinduism holistic holy homily humanism
humanist hymn
iconoclasm idealism ideology idol idolatry illumination imam
immaculate immortal immortality incarnation incense individualism
indulgence infallible infinite infinity initiation inquisition
inspiration intellect intercession interpretation intuition
islam islamic israelite
jesuit jew jewish jihad judgment justice justification
karma kosher
laity liberation liturgy logos lord lutheran
manifest martyr martyrdom materialism meditation mercy messiah
metaphysical metaphysics methodist millenarian millennium mindfulness
minister ministry miracle missionary modern modesty monastery monastic
monk monotheism moral morality mortal mosque muslim mystic mystical
mysticism myth mythology
naturalism nihilism nirvana noncognitivist nonviolence norm nun
oath objectivism offertory omnipotent omniscient ontology oracle
ordain ordination original orthodoxy
pagan pantheism parable paradigm paradise parish parochial passion
pastor pastoral patriarchal patron penance pentecostal perception
persecution phenomenology philanthropy philosophy pilgrim pilgrimage
pious pluralism polytheism pontificate pope postmodern pragmatism
prayer preach predestination prelate presbyterian priest priesthood
primordial principle prophecy prophet prophetess proselytize
protestant providence psalm psychology purgatory puritan purpose
quaker quantum quest
rabbi rabbinic radical rapture rationalism realm realism reason
reconciliation redemption reformation reincarnation relativism
relic relics religion renaissance repentance resurrection revelation
reverence revival righteous righteousness rite ritual rosary
sacrament sacred sacrifice sage saint salvation sanctity sanctuary
sangha satan satanism savior schism scholar scholastic scripture
sect secular secularism seminary sermon shaman shamanism shinto
shrine sin skepticism soul sovereignty spirit spiritual spirituality
stoicism sufi sufism summit supernatural superstition synagogue
tabernacle tao taoism temple testament theism theologian theological
theology theory theosophy torah tradition transcendence
transcendental trinity truth
unitarian universal universalism utilitarianism utopia
values vatican vespers vicar virtue vision vocation vow
worship wrath
yoga
zazen zen zoroastrian
""".split()

GAMING_ENTERTAINMENT = """
achievement addon adventure aiming ammo animation anime antagonist
arcade arena armor artstyle avatar
backlog barrel battleroyale beta blade blaster bonus boss buff
buildcraft bundle buytoplay
campaign casual character cinematic clan class claw clickable clip
codeword collectible combo command competitive completionist console
controller cooperative cosmetic cosplay counter craft crafting
crafter crash crawl creature credits crosshair crossplay crossplatform
crypt currency cutscene
damage deathmatch demo developer dialogue difficulty discord dlc dps
dungeon dupe duration
earlyaccess easter edition emote encounter endgame enemy engine epic
esports event exclusive expansion experience explora exploration
exploit
faction feature fighter final firstperson flashback flank fleet
foe freetoplay
gameplay gamer gaming genre ghost glitch griefer grind guild
handcrafted hardcore headset heal health healer hero highscore
hitbox hitpoints horde hud
idle immersive indie ingame instance inventory item
joystick
killstreak kingdom knight
lag lame launch league legendary level levelup lob lobby loot
lore
machinima main mana map matchmaker matchmaking melee meta metagame
miniboss minigame mission mmo mob mobile mod modder modding mode
monster mount multiplayer mythic
nerf newgame ninja npc
objective offline offscreen online openworld opponent optimization
overworld
pacifist paladin parkour party passive patch perk permadeath pickup
pixel platform platformer play playable playthrough plugin
pointandclick portal powerup preorder prestige progression pvp
puzzle
quest queue quicktime
raid random rank ranking ray reaction realm remaster replay
respawn retreat retry review roguelike roguelite roleplay round
rpg rts rubberbanding run runthrough
sandbox save savefile scaling scenario score scorecard screamer
semiopen sequel server session settlement shader shard shooter sidequest
sim simulation singleplayer skill skillset skin slash smash snipe
sniper solo souls spawn speedrun spell splash sprite squad stamina
stance stealth steampunk storyline strategy stream streaming stun
subscription survival swap swordplay
tank teamwork tech thirdperson thread tier tileset top topdown
tournament tracker trait tree trigger troll troop trophy turn
tutorial twitch
ult ultimate undead unlock update upgrade
vault versus victory villain virtual vr
walkthrough warp weapon webhook wield wiki worldbuilding wipe
xp
zone
""".split()

FINANCE_CRYPTO = """
accumulate airdrop allocation altcoin amortization annuity apy apr
arbitrage ascending asset atm
balance bandwidth bearish binance bitcoin blockchain bluechip bollinger
bond bootstrap bounty breakeven breakout bridging broker btc bubble
bull bullish buyback
candlestick capitalization cashout ceiling centralized chart circulating
coin collateral commodities compound consensus contract convergence
correlation counter crosschain cryptography cryptocurrency custodial
dao dapp debit decentralized defi deflation deflationary delisting
demand deposit depreciation derivative dex diamond difficulty dip
distributed divergence diversification dividend doge dollar dominance
double downtrend
earnings ecosystem ether ethereum etf exchange execution exit
exposure
faucet fiat fibonacci filling float floor fork fraud frictionless
fronting fund fundamental futures
gain gap gasprice gem genesis governance halving hardware hash
hashrate hedge high hodl holder holdings hot hyperinflation
ico illiquid immutable impermanent incentive index indicator
inflation initial injection institutional insurance interchain
interoperability investment investor iou
keystore
laddering launchpad layer lending leverage limit liquidation
liquidity listing locked longterm low lp
mainnet maker margin marketcap marketplace maxsupply memecoin
merger metaverse mev miner mining mint minting mixer momentum
moonshot mortgage moving multisig
nft node noncustodial
onchain onramp option oracle orderbook otc outflow outperform
overvalued
pair parity passive peg pegged pending perpetual pivot platform
portfolio position premining premium price privacy private
profitability proof protocol pullback pump
rally ratio rebalancing recovery redeem referral reflective
regulation relayer remittance reserve resistance retail retrace
return reversal risk roadmap roi rollup rugpull
satoshi scam scalability scaling seed selflimit settlement shard
sharding short signal slippage smart snapshot solana spot spread
stable stablecoin staking stochastic stop strategy subcustodial
supply support surge swap synthetic
taker telegram testnet ticker timestamp token tokenomics trading
transaction transfer treasury trend trendline trustless tvl
undercollateralized unlocked uptrend usdt utility
validation validator vault velocity verification virtual volatility
volume
wallet watchlist web whale whitepaper withdrawal
yield
zerofee zk zkrollup
""".split()

# Additional common words that don't fit neatly into categories
MISC_COMMON = """
abandoned ability abnormal aboard abolish absence absent absorb abstract abuse
accelerate accent acceptable acceptance accessible accessory accident
accommodate accomplish accomplishment accordance accountable accumulate
accuracy accuse accustom ache achieve acid acknowledge acquaint acquire
acre activate actual acute adapt adequate adhere adjacent adjust
admirable admission adopt adoption adorable adore advance advantage advent
adventure advertise advice advise advocate aerial affair affection afford
aftermath afternoon afterward agenda aggressive aging agitate agriculture
ahead aide aim aircraft aisle alarm album alert alien alike alive allegation
allege alliance allocate allowance alongside alter alternative altogether
aluminum amateur amazing ambassador ambiguous ambitious amid ammunition
ample amusement ancestor ancient angel anger angle ankle anniversary announce
annoy annual anonymous anticipate anxiety anybody anyhow anything anyway
anywhere apart apology apparatus apparent appetite applaud appliance
applicable appoint appraisal appreciation approach approval approximate
arbitrary archive arena arguably arise armor aroma arrange array arrow
artificial artwork ash aside aspiration assault assemble assert assessment
assign assist association assumption assurance assure asylum attach
attain attempt attendance attract attraction attribute auction audience
authentic authorize automatic automobile autumn availability avenue
awkward axis
backbone badge badly bail bake balanced balloon ban bang bare barely bargain
barrel bash batch bathe bay beam beard beast beforehand behalf behave
behavioral beloved bend beneath beneficial beside bet betray betting
bias bid bind biography bizarre blade blanket blast bleeding blessed
blessing blind bloom blown boast bold bolt bomb bond boom boost border
boring bounce boundary bow brake brave breakdown breast breathe breed
brick briefly brilliant broad broadly broadcast broke broker brutal bucket
buddy bulk bump bundle burden burst bury cabin cable calm cancer
capability capacity capital capture carbon career careful cargo casualty
catalog catch ceiling celebrate cemetery chain champion chaos chapter
chart chase cheap chef chemical chest chicken chip chronic chunk circuit
civilian clarity clash classification cling clinical clip closure cluster
coalition cognitive coincidence collapse collar colony combat combination
combine comedy comfort command commander commentary commercial
commission commitment commodity companion comparable compass compelling
compensate compete competent complaint complement complexity comply
comprise compromise concentrate conception conclude concrete
condemn conduct confess confidence configuration confine confirmation
confront confusion congressional connect conscience consciousness
consecutive conservation considerable consist consistent conspiracy
constant constitute constitutional constraint consultation consumer
consumption contemporary content contest continent continuous contrast
contribute controversial controversy conventional conversation convert
conviction cooperate cope core corporate correction correspond council
counter countless courage courtesy coverage crack craft crash creation
creative creature credibility crew criminal crisis criteria crop crucial
crush crystal cult cure curiosity currency curriculum custom cute cycle
daily damage dare database deadline dealer dear debate debris decent
decide declaration declare decline decrease deed defeat deficit deficit
definite delegate deliberately delivery democracy democratic demonstrate
dense depart depend deposit depression deputy derive descend desert deserve
desperate despite destroy destruction detailed detect determination device
devote dialogue dictate diet differ dimension dining diplomatic disability
disagree disappear disaster discipline disclose discourse discrimination
disorder display dispute distant distinct distinction distinguish
diverse diversity divine divorce doctrine dollar dominant dominate
donor dose double doubt dozen draft drag dramatic dramatically
drift drought drug dual duration dust duty dynamic eager earn ease eastern
eat echo edition editor effectively efficiency effort elaborate elderly
elegant eliminate elite elsewhere embrace embrace emission empire
emphasis emphasize empire employ employment empty enable encounter
encourage endless endure enforce engage engagement enormous enroll
enrollment entire entrance entrepreneur envelope episode equality equip
equivalent era essence essential establishment estate estimate evaluate
eventual evolve examination examine exceed excellent exception excessive
exchange excitement exclusive excuse execution exercise exhibit existence
exotic expand expansion expectation expense expertise explanation explicit
exploit expose exposure extend extent external extraordinary extreme
fabric facility fade faith fame familiar fantastic fate favorable feature
federal feedback fellow festival fiction fierce fight finding
fire fishing flag flat flavor fleet flesh flight float folk football
forecast forehead formula forth fortune foster foundation founder
fraction framework fraud freedom frequency frequent friendship
fuel fundamental funeral galaxy gap gear gender generous genius genuine
gesture glad glory grab gradually graduate grain graphics grave grip
grocery guaranteed guilt guitar habit habitat halfway halt handling
harbor heal headquarters height heir hence heritage hesitate highlight
hire holy homepage honest horrible household housing humor hungry hunt
hurt hypothesis identical identify identity ignorance illegal illustrate
imagination immigrant immigration immune implement implication imply impose
impressive inadequate incident incorporate incredible independence
independent indicate indicator indoor inevitable infant infection inflation
initial initially inner innocent innovation input insert insight
inspect inspiration install instance institutional instruction instructor
instrument insurance intellectual intelligence intend intense intention
interact interest interested interesting internal interpret intervention
introduction invasion invest investigation investor invisible
invitation involve iron island jail joke journalist judgment juice
junior jury justify keen knee knock laboratory ladder landscape laptop
largely laser latter launch lawn layer lean lecture legacy legitimate
length lesson liberal liberty license lifestyle lifetime limitation
line linguistic literally literary load lobby logical lonely loop lover
luck luxury magic mainstream maker makeup mandate manner manufacturer
margin marine mask massive mate material maximize mechanism media
medium melt membership mental mere merely mess methodology midfield
mild military mine mineral minimum minister minor miracle mirror
moderate modify mom momentum monitor monthly mood moreover mortgage
mountain multiple municipal murder mystery myth naked narrative
naturally naval nearby neat necessarily necessity negative negotiate
neither nerve nervous nevertheless newly nightmare noble nod
nonetheless nonetheless northeast notion notion novel nowhere nuclear
numerous nurse nutritional obey objection obligation obtain occasional
occupy ocean odd odds offend offense offensive offering officer
ongoing operate operation operator opponent oppose opposite optional
orange ordinary organic organization organize orientation origin
originally outcome outdoor outer output outstanding overall overcome
overlook overnight overwhelm ownership pace pack pan panel participate
partly partnership passage passenger passive path patience pause
peaceful penalty perfect permanent permission persist perspective pet
philosopher philosophy photograph physician pickup pile pilot
pioneer pitch planet plaza plot plunge plus poem poetry pointed
pool poorly popular popularity portion portrait portray pose
possess possibility postpone potential pound pour poverty powder
pray precise precisely predict predominantly preference pregnancy
prejudice premise premium preparation prescription presidency
presidential press presumably presumably pretend primarily primarily
primary prime princess principal prior priority prisoner privacy
privately probe proceed processed productive profession professional
profile profound progressive prominent promise promote prompt
proof properly proportion proposal propose prosecution protect
protein protest proud prove provision psychological publication publicly
punch purchase purely pursue puzzle qualify quit quote racial racism
radical rage rain rank rape rapid rare rating reach react reader
readily real realistic reality realize reasonable rebel rebuild recall
receiver reception recognize recommendation recover recovery recruit
reduction reflection reform regime regional register regulation
rehabilitation reinforce reject relate relax release relief religion
rely remaining remarkable remedy remind remote removal render repeat
repeatedly replace representation reproduce republican reputation request
require rescue resemble residential resign resist resolution resolve
resort resource respond restoration restore restriction retain retreat
reveal reverse rhetoric rifle rip rival rope roughly route royal
ruin rush sacred sacrifice sake sand satellite satisfaction satisfy
scale scandal scared scenario scholar scholarship scientific scope
sculpture second secure segment selection senator senior sense
sensitive separate sequence session setting severe sexual shall shape
shareholder shelter shift ship shock shortly shot shoulder shout
signal significance silent silly similarity similarly sin singer sink
sir situated sixth slap slavery slight slightly slip smoke snap
soccer solely somebody somehow somewhat soul southern span spark
speaker specify spectrum sphere spiritual spokesman spread stability
stable stadium stem stick stimulate stir stock stomach strain stranger
strategic strictly string strip stroke structural struggle studio submit
subsequent substance substantial subtle suburban successive sue
sugar suicide suit summer super surely surface surgery surprise
surrender survival survive suspect suspend suspension sustain swear
swim swing symbol syndrome tabloid tackle tale tank tap target
taste teen temple temporary tendency tender tennis tension tenure
testify testimony thank theater therapeutic thick thin thoroughly
threat threaten thriller throughout tighten timber tissue tobacco
tolerate toll tomorrow tongue topic torch total totally tough tourist
tournament trace tradition tragedy trail transform transmission
transport trap trauma tremendous tribe tribunal trick trigger
triumph troops tropical truly tumor tunnel twist typical ugly
ultimately unable uncle undergo uniform unify unite unity universal
unlike unlikely unusual update upgrade upon upper upset urban
utility utterly vacation valuable varies vast veteran viable
victim victory violation virtue visible visitor visual vital
vivid volunteer vote wage wander warming wealth weapon weather
wedding weekly welfare wheat whenever wherever whisper widely
wildlife willing wind wing wire wisdom withdraw witness wonder
wooden working workplace workshop worldwide worry worship worthy
wrap zone
""".split()


def main():
    print(f"Loading existing dictionary from {DICT_PATH}...")
    existing_words = load_existing()
    existing_set = set(existing_words)
    print(f"  Existing words: {len(existing_words)}")

    # Gather all category words
    all_categories = {
        "technology": TECHNOLOGY,
        "academic": ACADEMIC,
        "business": BUSINESS,
        "medical": MEDICAL,
        "science": SCIENCE,
        "informal/slang": INFORMAL_SLANG,
        "names/proper": NAMES_AND_PROPER,
        "food/cooking": FOOD_COOKING,
        "sports/fitness": SPORTS_FITNESS,
        "emotions/personality": EMOTIONS_PERSONALITY,
        "household": HOUSEHOLD,
        "nature/weather": NATURE_WEATHER,
        "legal": LEGAL,
        "music/art": MUSIC_ART,
        "travel/geography": TRAVEL_GEOGRAPHY,
        "fashion/clothing": FASHION_CLOTHING,
        "education": EDUCATION,
        "abbreviations": COMMON_ABBREVIATIONS,
        "computing_extra": COMPUTING_EXTRA,
        "animals": ANIMALS,
        "colors/materials": COLORS_MATERIALS,
        "verbs": VERBS_COMMON,
        "adjectives": ADJECTIVES_COMMON,
        "adverbs": ADVERBS_COMMON,
        "prepositions": PREPOSITIONS_CONJUNCTIONS,
        "body_parts": BODY_PARTS,
        "professions": PROFESSIONS,
        "time/calendar": TIME_CALENDAR,
        "internet/social": INTERNET_SOCIAL,
        "compound_words": COMPOUND_WORDS,
        "additional_verbs": ADDITIONAL_VERBS,
        "additional_nouns": ADDITIONAL_NOUNS,
        "additional_adjectives": ADDITIONAL_ADJECTIVES,
        "real_estate": REAL_ESTATE_HOME,
        "environment": ENVIRONMENT_ENERGY,
        "philosophy/religion": PHILOSOPHY_RELIGION,
        "gaming/entertainment": GAMING_ENTERTAINMENT,
        "finance/crypto": FINANCE_CRYPTO,
        "additional_verbs2": ADDITIONAL_VERBS2,
        "additional_nouns2": ADDITIONAL_NOUNS2,
        "final_batch": FINAL_BATCH,
        "misc_common": MISC_COMMON,
    }

    new_words = set()
    for cat_name, cat_words in all_categories.items():
        cat_new = set()
        for w in cat_words:
            w = w.strip().lower()
            if w and w not in existing_set and w not in new_words:
                # Only valid words: alphabetic, 2+ chars
                if w.isalpha() and len(w) >= 2:
                    cat_new.add(w)
                    new_words.add(w)
        print(f"  {cat_name}: {len(cat_new)} new words")

    print(f"\n  Total new from categories: {len(new_words)}")

    # Morphological expansion to reach target
    needed = TARGET_COUNT - len(existing_words) - len(new_words)
    if needed > 0:
        print(f"\n  Need {needed} more words, running morphological expansion...")
        all_known = existing_set | new_words

        # Use POS-aware morphological expansion
        verb_roots = set(VERBS_COMMON + ADDITIONAL_VERBS + ADDITIONAL_VERBS2)
        adj_roots = set(ADJECTIVES_COMMON + ADDITIONAL_ADJECTIVES + EMOTIONS_PERSONALITY)
        # Nouns: everything except verbs, adjectives, proper nouns, abbreviations
        proper_set = set(w.lower() for w in NAMES_AND_PROPER)
        abbrev_set = set(w.lower() for w in COMMON_ABBREVIATIONS)
        noun_candidates = (set(TECHNOLOGY) | set(MEDICAL) | set(SCIENCE) |
                          set(FOOD_COOKING) | set(ANIMALS) | set(HOUSEHOLD) |
                          set(NATURE_WEATHER) | set(SPORTS_FITNESS) |
                          set(PROFESSIONS) | set(MUSIC_ART) | set(LEGAL) |
                          set(BUSINESS) | set(ADDITIONAL_NOUNS) |
                          set(COMPOUND_WORDS) | set(EDUCATION) |
                          set(TRAVEL_GEOGRAPHY) | set(FASHION_CLOTHING) |
                          set(BODY_PARTS) | set(REAL_ESTATE_HOME) |
                          set(ENVIRONMENT_ENERGY) | set(PHILOSOPHY_RELIGION) |
                          set(GAMING_ENTERTAINMENT) | set(FINANCE_CRYPTO) |
                          set(INTERNET_SOCIAL) | set(TIME_CALENDAR) |
                          set(COLORS_MATERIALS))
        noun_roots = noun_candidates - proper_set - abbrev_set - verb_roots - adj_roots

        morphed = morphological_expand(verb_roots, adj_roots, noun_roots)
        morphed -= all_known  # remove words we already have

        # Take only what we need, sorted for determinism
        morphed_sorted = sorted(morphed)
        if len(morphed_sorted) > needed:
            morphed_sorted = morphed_sorted[:needed]

        new_words.update(morphed_sorted)
        print(f"  Added {len(morphed_sorted)} morphological variants")

    # Build final list: existing order first, then new words sorted
    new_words_sorted = sorted(new_words)
    final_words = existing_words + new_words_sorted

    # Trim or report
    if len(final_words) > TARGET_COUNT:
        final_words = final_words[:TARGET_COUNT]

    print(f"\n  Final word count: {len(final_words)}")

    # Write back
    with open(DICT_PATH, "w", encoding="utf-8", newline="\n") as f:
        for w in final_words:
            f.write(w + "\n")

    print(f"  Written to {DICT_PATH}")
    print("  Done!")


if __name__ == "__main__":
    main()
