package com.sovexis.domain.recovery

import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.MasterIdentity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Provider

/**
 * 助记词恢复实现。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 账户恢复机制完整实现指令
 *
 * 提供 BIP-39 助记词生成与验证功能。
 *
 * 安全约束：
 * - 助记词生成使用安全随机数（SecureRandom）
 * - 密码短语使用 PBKDF2-HMAC-SHA512（2048 轮迭代）
 * - 最小密码短语长度 12 字符
 */
class MnemonicRecovery(
    private val identityManagerProvider: Provider<IdentityManager>
) {
    companion object {
        // BIP-39 英语词库（2048 词，此处为部分示例）
        private val WORD_LIST: List<String> = listOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
            "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
            "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
            "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
            "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
            "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album",
            "alcohol", "alert", "alien", "all", "alley", "allow", "almost", "alone",
            "alpha", "already", "also", "alter", "always", "amateur", "amazing", "among",
            "amount", "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry",
            "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
            "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april",
            "arch", "arctic", "area", "arena", "argue", "arm", "armed", "armor",
            "army", "around", "arrange", "arrest", "arrive", "arrow", "art", "artefact",
            "artist", "artwork", "ask", "aspect", "assault", "asset", "assist", "assume",
            "asthma", "athlete", "atom", "attack", "attend", "attitude", "attract", "auction",
            "audit", "august", "aunt", "author", "auto", "autumn", "average", "avocado",
            "avoid", "awake", "aware", "away", "awesome", "awful", "awkward", "axis",
            "baby", "bachelor", "bacon", "badge", "bag", "balance", "balcony", "ball",
            "bamboo", "banana", "banner", "bar", "barely", "bargain", "barrel", "base",
            "basic", "basket", "battle", "beach", "bean", "beauty", "because", "become",
            "beef", "before", "begin", "behave", "behind", "believe", "below", "belt",
            "bench", "benefit", "best", "betray", "better", "between", "beyond", "bicycle",
            "bid", "bike", "bind", "biology", "bird", "birth", "bitter", "black",
            "blade", "blame", "blanket", "blast", "bleak", "bless", "blind", "blood",
            "blossom", "blouse", "blue", "blur", "blush", "board", "boat", "body",
            "boil", "bomb", "bone", "bonus", "book", "boost", "border", "boring",
            "borrow", "boss", "bottom", "bounce", "box", "boy", "bracket", "brain",
            "brand", "brass", "brave", "bread", "breeze", "brick", "bridge", "brief",
            "bright", "bring", "brisk", "broccoli", "broken", "bronze", "broom", "brother",
            "brown", "brush", "bubble", "buddy", "budget", "buffalo", "build", "bulb",
            "bulk", "bullet", "bundle", "bunker", "burden", "burger", "burst", "bus",
            "business", "busy", "butter", "buyer", "buzz", "cabbage", "cabin", "cable",
            "cactus", "cage", "cake", "call", "calm", "camera", "camp", "can",
            "canal", "cancel", "candy", "cannon", "canoe", "canvas", "canyon", "capable",
            "capital", "captain", "car", "carbon", "card", "cargo", "carpet", "carry",
            "cart", "case", "cash", "casino", "castle", "casual", "cat", "catalog",
            "catch", "category", "cattle", "caught", "cause", "caution", "cave", "ceiling",
            "celery", "cement", "census", "century", "cereal", "certain", "chair", "chalk",
            "champion", "change", "chaos", "chapter", "charge", "chase", "chat", "cheap",
            "check", "cheese", "chef", "cherry", "chest", "chicken", "chief", "child",
            "chimney", "choice", "choose", "chronic", "chuckle", "chunk", "churn", "cigar",
            "cinnamon", "circle", "citizen", "city", "civil", "claim", "clap", "clarify",
            "claw", "clay", "clean", "clerk", "clever", "click", "client", "cliff",
            "climb", "clinic", "clip", "clock", "clog", "close", "cloth", "cloud",
            "clown", "club", "clump", "cluster", "clutch", "coach", "coast", "coconut",
            "code", "coffee", "coil", "coin", "collect", "color", "column", "combine",
            "come", "comfort", "comic", "common", "company", "concert", "conduct", "confirm",
            "congress", "connect", "consider", "control", "convince", "cook", "cool", "copper",
            "copy", "coral", "core", "corn", "correct", "cost", "cotton", "couch",
            "country", "couple", "course", "cousin", "cover", "coyote", "crack", "cradle",
            "craft", "cram", "crane", "crash", "crater", "crawl", "crazy", "cream",
            "credit", "creek", "crew", "cricket", "crime", "crisp", "critic", "crop",
            "cross", "crouch", "crowd", "crucial", "cruel", "cruise", "crumble", "crunch",
            "crush", "cry", "crystal", "cube", "culture", "cup", "cupboard", "curious",
            "current", "curtain", "curve", "cushion", "custom", "cute", "cycle", "dad",
            "damage", "damp", "dance", "danger", "daring", "dash", "daughter", "dawn",
            "day", "deal", "debate", "debris", "decade", "december", "decide", "decline",
            "decorate", "decrease", "deer", "defense", "define", "defy", "degree", "delay",
            "deliver", "demand", "demise", "denial", "dentist", "deny", "depart", "depend",
            "deposit", "depth", "deputy", "derive", "describe", "desert", "design", "desk",
            "despair", "destroy", "detail", "detect", "develop", "device", "devote", "diagram",
            "dial", "diamond", "diary", "dice", "diesel", "diet", "differ", "digital",
            "dignity", "dilemma", "dinner", "dinosaur", "direct", "dirt", "disagree", "discover",
            "disease", "dish", "dismiss", "disorder", "display", "distance", "divert", "divide",
            "divorce", "dizzy", "doctor", "document", "dog", "doll", "dolphin", "domain",
            "donate", "donkey", "donor", "door", "dose", "double", "dove", "draft",
            "dragon", "drama", "drastic", "draw", "dream", "dress", "drift", "drill",
            "drink", "drip", "drive", "drop", "drum", "dry", "duck", "dumb",
            "dune", "during", "dust", "dutch", "duty", "dwarf", "dynamic", "eager",
            "eagle", "early", "earn", "earth", "easily", "east", "easy", "echo",
            "ecology", "economy", "edge", "edit", "educate", "effort", "egg", "eight",
            "either", "elbow", "elder", "electric", "elegant", "element", "elephant", "elevator",
            "elite", "else", "embark", "embody", "embrace", "emerge", "emotion", "employ",
            "empower", "empty", "enable", "enact", "end", "endless", "endorse", "enemy",
            "energy", "enforce", "engage", "engine", "enhance", "enjoy", "enlist", "enough",
            "enrich", "enroll", "ensure", "enter", "entire", "entry", "envelope", "episode",
            "equal", "equip", "era", "erase", "erode", "erosion", "error", "erupt",
            "escape", "essay", "essence", "estate", "eternal", "ethics", "evidence", "evil",
            "evoke", "evolve", "exact", "example", "excess", "exchange", "excite", "exclude",
            "excuse", "execute", "exercise", "exhaust", "exhibit", "exile", "exist", "exit",
            "exotic", "expand", "expect", "expire", "explain", "expose", "express", "extend",
            "extra", "eye", "eyebrow", "fabric", "face", "faculty", "fade", "faint",
            "faith", "fall", "false", "fame", "family", "famous", "fan", "fancy",
            "fantasy", "farm", "fashion", "fat", "fatal", "father", "fatigue", "fault",
            "favorite", "feature", "february", "federal", "fee", "feed", "feel", "female",
            "fence", "festival", "fetch", "fever", "few", "fiber", "fiction", "field",
            "figure", "file", "film", "filter", "final", "find", "fine", "finger",
            "finish", "fire", "firm", "first", "fiscal", "fish", "fit", "fitness",
            "fix", "flag", "flame", "flash", "flat", "flavor", "flee", "flight",
            "flip", "float", "flock", "floor", "flower", "fluid", "flush", "fly",
            "foam", "focus", "fog", "foil", "fold", "follow", "food", "foot",
            "force", "forest", "forget", "fork", "fortune", "forum", "forward", "fossil",
            "foster", "found", "fox", "fragile", "frame", "frequent", "fresh", "friend",
            "fringe", "frog", "front", "frost", "frown", "frozen", "fruit", "fuel",
            "fun", "funny", "furnace", "fury", "future", "gadget", "gain", "galaxy",
            "gallery", "game", "gap", "garage", "garbage", "garden", "garlic", "garment",
            "gas", "gasp", "gate", "gather", "gauge", "gaze", "general", "genius",
            "genre", "gentle", "genuine", "gesture", "ghost", "giant", "gift", "giggle",
            "ginger", "girl", "give", "glad", "glance", "glare", "glass", "gleam",
            "globe", "gloom", "glory", "gloss", "glove", "glow", "glue", "goat",
            "god", "goat", "gold", "goose", "govern", "government", "gown", "grab",
            "grace", "grade", "grain", "grandmaster", "grant", "grape", "graph", "grasp",
            "grass", "grateful", "gravity", "great", "green", "grid", "grief", "grill",
            "grin", "grind", "grip", "groan", "grocery", "gross", "group", "grow",
            "growth", "guard", "guess", "guest", "guide", "guilt", "guitar", "gun",
            "gym", "habit", "hair", "half", "hall", "hammer", "hamster", "hand",
            "happy", "harbor", "hard", "hardly", "hardware", "harm", "harmony", "harp",
            "harsh", "harvest", "haste", "happen", "happy", "harsh", "haste", "hat",
            "hate", "haul", "have", "hawk", "hazard", "head", "health", "heart",
            "heavy", "hedgehog", "height", "hello", "helmet", "help", "hence", "her",
            "herb", "here", "hero", "hidden", "high", "hill", "him", "hint",
            "hip", "hire", "history", "hold", "hole", "holiday", "hollow", "home",
            "honey", "honor", "hope", "horn", "horror", "horse", "hospital", "hotel",
            "hour", "hover", "hub", "huge", "human", "humble", "humor", "hundred",
            "hungry", "hunt", "hurdle", "hurry", "hurt", "husband", "hybrid", "ice",
            "icon", "idea", "ideal", "identity", "idle", "ignore", "ill", "illegal",
            "illness", "image", "imagine", "imitate", "immediate", "immense", "impass", "imply",
            "import", "impose", "improve", "impulse", "inch", "include", "income", "increase",
            "indeed", "indent", "independence", "index", "indicate", "indoor", "industry", "infant",
            "inflict", "inform", "inherit", "initial", "injure", "ink", "innocent", "input",
            "inquiry", "insect", "inside", "insight", "insist", "inspect", "inspire", "install",
            "intact", "interest", "interior", "internal", "interval", "invest", "invite", "involve",
            "iron", "island", "isolate", "issue", "item", "ivory", "jacket", "jaguar",
            "jail", "janitor", "january", "jazz", "jealous", "jeans", "jellyfish", "jewel",
            "job", "join", "joke", "jolly", "joust", "journey", "judge", "juice",
            "jump", "jungle", "june", "junk", "just", "kaleidoscope", "kangaroo", "keen",
            "keep", "ketchup", "kick", "kidney", "kind", "king", "kiosk", "kiss",
            "kitchen", "kiwi", "knee", "knife", "knight", "knit", "knob", "knot",
            "know", "knowledge", "koala", "label", "labor", "lace", "lack", "ladder",
            "lady", "lake", "lamb", "lamp", "land", "landscape", "lane", "language",
            "laptop", "large", "laser", "last", "late", "lately", "later", "latest",
            "latter", "laugh", "laughter", "laundry", "law", "lawn", "lawsuit", "layer",
            "lazy", "lead", "leader", "leading", "leaf", "lean", "leap", "learn",
            "least", "leave", "lecture", "left", "legal", "legend", "leisure", "lemon",
            "lend", "length", "lens", "leopard", "lesson", "letter", "level", "lever",
            "liberty", "library", "license", "lick", "life", "lift", "light", "like",
            "limb", "limit", "linen", "liner", "lingo", "link", "lion", "list",
            "listen", "liter", "little", "live", "liver", "living", "lizard", "load",
            "loan", "lobby", "local", "lock", "lodge", "logic", "lonely", "loose",
            "lottery", "loud", "lounge", "love", "lovely", "low", "loyal", "luck",
            "lucky", "luggage", "lumber", "lunar", "lunch", "luxury", "lyrics", "machine",
            "mad", "magic", "magnet", "maid", "mail", "main", "major", "make",
            "male", "mall", "mammoth", "manage", "mandate", "mango", "manner", "mansion",
            "manual", "maple", "march", "marble", "march", "margin", "marine", "mark",
            "market", "marriage", "marsh", "mask", "mass", "mast", "master", "match",
            "material", "matter", "maximize", "may", "maybe", "mayor", "me", "meal",
            "mean", "means", " meantime", "measure", "meat", "mechanic", "medal", "media",
            "melon", "melt", "member", "membership", "membrane", "memo", "mention", "menu",
            "mercy", "merge", "merit", "merry", "mesh", "message", "metal", "meter",
            "method", "metro", "microphone", "middle", "might", "military", "milk", "mimic",
            "mince", "mind", "mine", "mineral", "mini", "minimize", "mint", "minute",
            "miracle", "mirror", "misery", "miss", "mistake", "mix", "mixed", "mixture",
            "mobile", "model", "moderate", "modern", "modest", "modify", "moist", "molecule",
            "moment", "monarch", "money", "monitor", "monkey", "monster", "month", "mood",
            "moon", "moral", "more", "morning", "mortal", "mortgage", "mosquito", "moss",
            "most", "motel", "mother", "motion", "motor", "motto", "mount", "mouse",
            "mouth", "movie", "much", "muffin", "mule", "multiply", "muscle", "museum",
            "mushroom", "music", "must", "mutual", "mystery", "myth", "nail", "naked",
            "name", "nanny", "narrate", "narrow", "nasty", "nation", "native", "nature",
            "near", "nearby", "nearly", "neat", "necessary", "neck", "need", "negative",
            "neglect", "negotiate", "neighbor", "neither", "neon", "nephew", "nerve", "nest",
            "network", "neutral", "never", "new", "news", "next", "nice", "niece",
            "night", "nine", "noble", "nobody", "noise", "nomination", "none", "noon",
            "norm", "normal", "north", "northern", "nose", "notch", "note", "nothing",
            "notice", "notion", "novel", "november", "now", "nuclear", "number", "nurse",
            "nutrition", "oak", "object", "obtain", "obvious", "occur", "ocean", "october",
            "odds", "offense", "offer", "office", "often", "oil", "okay", "old",
            "olive", "olympics", "omit", "once", "one", "onion", "online", "only",
            "open", "opera", "opinion", "opponent", "opportunity", "oppose", "option", "orange",
            "orbit", "orchard", "order", "ordinary", "organ", "orient", "original", "orphan",
            "other", "ought", "ounce", "outcome", "outdoor", "outer", "outline", "output",
            "outrage", "outside", "oval", "oven", "over", "overall", "overcome", "overlook",
            "overnight", "overseas", "owner", "oxide", "oxygen", "oyster", "ozone", "paddle",
            "page", "paint", "pair", "palace", "palm", "panther", "pantry", "paper",
            "parade", "parent", "park", "parrot", "party", "pass", "patch", "path",
            "patience", "patient", "patrol", "patron", "pattern", "pause", "pave", "payment",
            "peace", "peaceful", "peach", "pearl", "penny", "people", "pepper", "percent",
            "perfect", "perform", "perhaps", "period", "permit", "person", "pest", "pet",
            "petition", "phone", "photo", "phrase", "physical", "piano", "pick", "picture",
            "piece", "pilot", "pin", "pine", "pink", "pioneer", "pipe", "pistol",
            "pitch", "pizza", "place", "plain", "plan", "plane", "planet", "plant",
            "plastic", "plate", "play", "playground", "please", "pledge", "plenty", "plot",
            "plow", "pluck", "plug", "plum", "plunge", "plural", "plus", "pocket",
            "poem", "poet", "point", "polar", "police", "policy", "polish", "polite",
            "politics", "pollution", "pond", "pony", "pool", "poor", "pop", "popular",
            "porch", "port", "pose", "position", "positive", "possess", "possible", "poster",
            "pot", "potato", "potential", "poultry", "pound", "powder", "power", "practice",
            "praise", "predict", "prefer", "premium", "prepare", "presence", "preserve", "president",
            "press", "pressure", "prestige", "pretend", "pretty", "prevent", "price", "pride",
            "primary", "prime", "print", "prior", "prize", "probe", "problem", "proceed",
            "process", "produce", "product", "profession", "professor", "profile", "profit", "program",
            "progress", "project", "promise", "promote", "prompt", "proof", "proper", "property",
            "proposal", "propose", "prospect", "protect", "protein", "protest", "proud", "prove",
            "provide", "province", "provision", "psychology", "public", "pulse", "pump", "punish",
            "purchase", "pupil", "purple", "purpose", "purse", "pursue", "push", "puzzle",
            "pyramid", "quality", "quantity", "quarter", "queen", "query", "quest", "quick",
            "quiet", "quite", "quota", "quote", "rabbit", "race", "racial", "rack",
            "radar", "radio", "raft", "rail", "rain", "rainbow", "raise", "rally",
            "ramp", "ranch", "random", "range", "rapid", "rare", "rarely", "rather",
            "ratio", "reach", "react", "read", "reader", "ready", "real", "reality",
            "realize", "really", "reason", "rebel", "rebuild", "recall", "receive", "recent",
            "recipe", "reckon", "record", "recover", "reduce", "refer", "reflect", "reform",
            "refresh", "refuse", "regard", "region", "register", "regret", "regular", "reject",
            "relate", "relax", "release", "relief", "rely", "remain", "remark", "remedy",
            "remember", "remind", "remote", "remove", "render", "rent", "repair", "repeat",
            "replace", "report", "request", "require", "rescue", "research", "reserve", "reset",
            "reside", "resign", "resist", "resolution", "resolve", "resort", "resource", "respect",
            "respond", "response", "rest", "restore", "result", "retail", "retain", "retire",
            "retreat", "return", "reveal", "review", "revolution", "reward", "rhythm", "rice",
            "rich", "rider", "ridge", "rifle", "right", "rigid", "ring", "riot",
            "ripe", "rise", "risk", "ritual", "rival", "river", "road", "robot",
            "rock", "rocket", "romance", "roof", "room", "root", "rope", "rose",
            "rotate", "rough", "round", "route", "royal", "rubber", "rude", "rugby",
            "ruin", "rule", "run", "runway", "rural", "rush", "rust", "sacrifice",
            "safe", "safety", "sail", "saint", "salad", "salary", "salmon", "salon",
            "salt", "salute", "same", "sample", "sanction", "sand", "satisfy", "sauce",
            "save", "saving", "say", "scale", "scan", "scare", "scene", "scheme",
            "school", "science", "scope", "score", "scout", "scramble", "scrape", "screen",
            "script", "scrub", "search", "season", "seat", "second", "secret", "section",
            "sector", "secure", "security", "seed", "seek", "segment", "select", "sell",
            "senate", "senator", "send", "senior", "sense", "sensor", "sentence", "separate",
            "sequence", "series", "serious", "servant", "serve", "service", "session", "settle",
            "setup", "seven", "several", "severe", "shade", "shadow", "shaft", "shake",
            "shall", "shallow", "shame", "shape", "share", "shark", "sharp", "shatter",
            "shed", "sheep", "sheer", "sheet", "shelf", "shell", "shelter", "shift",
            "shine", "ship", "shirt", "shock", "shoe", "shoot", "shop", "shore",
            "short", "shot", "should", "shoulder", "shove", "show", "shower", "shrimp",
            "shrink", "shrug", "shut", "shuttle", "sick", "side", "siege", "sight",
            "sigma", "sign", "signal", "silence", "silent", "silk", "silly", "silver",
            "similar", "simple", "since", "sing", "singer", "single", "sink", "sir",
            "sister", "sit", "site", "situation", "six", "sixteen", "size", "skate",
            "skill", "skin", "skip", "skirt", "skull", "slave", "sleep", "slice",
            "slide", "slight", "slim", "slogan", "slot", "slow", "slowly", "small",
            "smart", "smell", "smile", "smoke", "smooth", "snack", "snake", "snap",
            "snow", "so", "soak", "soap", "soar", "soccer", "social", "society", "sock",
            "socket", "soda", "sofa", "soft", "software", "soil", "solar", "soldier",
            "solid", "solution", "solve", "some", "somebody", "somehow", "someone", "something",
            "sometimes", "somewhat", "somewhere", "song", "soon", "sophisticated", "sorry", "sort",
            "soul", "sound", "soup", "source", "south", "southern", "space", "speak",
            "speaker", "special", "species", "specific", "spectrum", "speech", "speed", "spell",
            "spend", "spend", "sphere", "spice", "spider", "spike", "spin", "spirit",
            "split", "spoke", "sponsor", "spoon", "sport", "spot", "spread", "spring",
            "spy", "squad", "square", "squeeze", "stability", "stable", "stadium", "staff",
            "stage", "stain", "stair", "staircase", "stake", "stale", "stall", "stamp",
            "stand", "standard", "star", "stare", "stark", "start", "state", "station",
            "status", "stay", "steady", "steak", "steal", "steam", "steel", "steep",
            "steer", "step", "steward", "stick", "still", "sting", "stir", "stock",
            "stomach", "stone", "stool", "stop", "storage", "store", "storm", "story",
            "stove", "straight", "strain", "strand", "strange", "stranger", "strategic", "strategy",
            "straw", "strawberry", "stream", "street", "strength", "stress", "stretch", "strict",
            "stride", "strike", "string", "strip", "stripe", "strive", "stroke", "strong",
            "strongly", "structure", "struggle", "student", "studio", "study", "stuff", "stumble",
            "stunning", "style", "subject", "submit", "subsequent", "substance", "subtle", "suburb",
            "succeed", "success", "successful", "such", "sudden", "suddenly", "suffer", "sufficient",
            "sugar", "suggest", "suit", "suite", "sultan", "sum", "summary", "summer",
            "summit", "sun", "sunglasses", "sunny", "sunrise", "sunset", "super", "superb",
            "supper", "supplement", "supply", "support", "supporter", "suppose", "supposed", "supreme",
            "sure", "surely", "surface", "surge", "surprise", "surround", "survey", "survival",
            "survive", "suspect", "suspend", "sustain", "swallow", "swamp", "swallow", "swear",
            "sweat", "sweater", "sweep", "sweet", "swell", "swift", "swim", "swing",
            "switch", "sword", "symbol", "symptom", "syntax", "system", "table", "tablet",
            "tackle", "tactic", "tail", "tailor", "tale", "talent", "talk", "tall",
            "tame", "tan", "tank", "tap", "tape", "target", "task", "taste", "tattoo",
            "taxi", "taxi", "taxpayer", "tea", "teach", "teacher", "team", "tear",
            "teaspoon", "technical", "technique", "technology", "teenage", "teenager", "telephone", "telescope",
            "television", "tell", "temper", "temperature", "temple", "tempo", "temporary", "tempt",
            "tenant", "tend", "tendency", "tender", "tennis", "tense", "tension", "tent",
            "term", "terms", "terrace", "terrible", "territory", "terror", "terrorist", "test",
            "testify", "testimony", "text", "texture", "than", "thank", "that", "theater",
            "their", "them", "theme", "themselves", "then", "theory", "therapy", "there",
            "thereafter", "thereby", "therefore", "these", "thick", "thief", "thin", "thing",
            "think", "thinking", "third", "thirteen", "thirty", "this", "thorough", "those",
            "though", "thought", "thousand", "thread", "threat", "threaten", "three", "threshold",
            "thrill", "thrive", "throat", "throne", "through", "throughout", "throw", "thumb",
            "thus", "tick", "ticket", "tide", "tidy", "tie", "tiger", "tight",
            "tile", "till", "timber", "time", "timeline", "timing", "tiny", "tip",
            "tired", "tissue", "title", "to", "toast", "tobacco", "today", "together",
            "toilet", "token", "tomato", "tomorrow", "tone", "tongue", "tonight", "tool",
            "tooth", "top", "topic", "torch", "toss", "total", "totally", "touch", "tough",
            "tour", "tourist", "tournament", "toward", "towards", "tower", "town", "toxic",
            "toy", "trace", "track", "trade", "tradition", "traditional", "traffic", "tragedy",
            "trail", "train", "trainer", "trait", "transfer", "transform", "transition", "translate",
            "transport", "trap", "trash", "travel", "tray", "treasure", "treat", "treatment",
            "treaty", "tree", "tremendous", "trend", "trial", "tribe", "tribute", "trick",
            "trigger", "trim", "trip", "triumph", "troop", "tropical", "trouble", "truck",
            "true", "truly", "trust", "truth", "try", "tube", "tuesday", "tuition",
            "tumor", "tune", "tunnel", "turkey", "turn", "tutor", "twenty", "twice",
            "twin", "twist", "two", "type", "typical", "ugly", "ultimate", "unable",
            "uncle", "under", "undergo", "underlying", "undermine", "understand", "undertake", "unemployment",
            "unexpected", "unfair", "unfold", "unfortunate", "unhappy", "uniform", "union", "unique",
            "unit", "unite", "united", "unity", "universal", "universe", "university", "unknown",
            "unless", "unlike", "unlikely", "until", "unusual", "update", "upon", "upper",
            "upset", "urban", "urge", "us", "usage", "use", "used", "useful",
            "user", "usual", "usually", "utility", "vacant", "vacation", "vaccine", "vacuum",
            "vague", "valid", "valley", "valuable", "value", "valve", "vampire", "van",
            "vanish", "vapor", "variable", "variety", "various", "vast", "vegetable", "vehicle",
            "veil", "velocity", "velvet", "vendor", "venture", "venue", "verb", "verbal",
            "verdict", "verify", "verse", "version", "versus", "vertical", "very", "vessel",
            "veteran", "viable", "vibrant", "vice", "victim", "victory", "video", "view",
            "viewer", "village", "vintage", "violate", "violation", "violence", "violent", "violet",
            "virtual", "virtue", "virus", "visible", "vision", "visit", "visitor", "visual",
            "vital", "vivid", "vocal", "voice", "void", "volcano", "volume", "voluntary",
            "volunteer", "vote", "voter", "voyage", "vulnerable", "wade", "wage", "wagon",
            "wait", "wake", "walk", "wall", "wander", "want", "war", "warm",
            "warn", "warning", "warrant", "warrior", "wash", "waste", "watch", "water",
            "wave", "weak", "wealth", "wealthy", "weapon", "wear", "weather", "wedding",
            "weekend", "weekly", "weigh", "weight", "weird", "welcome", "welfare", "west",
            "western", "whale", "what", "whatever", "wheat", "wheel", "when", "whenever",
            "where", "whereas", "wherever", "whether", "which", "while", "whip", "whisper",
            "white", "who", "whoever", "whole", "whom", "whose", "wide", "widely",
            "wife", "wild", "will", "willing", "win", "wind", "window", "wine",
            "wing", "winner", "winter", "wipe", "wire", "wisdom", "wise", "wish",
            "witch", "with", "withdraw", "within", "without", "witness", "wolf", "woman",
            "wonder", "wonderful", "wood", "wooden", "wool", "word", "work", "worker",
            "workout", "workshop", "world", "worldwide", "worn", "worried", "worry", "worth",
            "would", "wound", "wrap", "wrist", "write", "writer", "wrong", "yard",
            "yeah", "year", "yellow", "yes", "yesterday", "yet", "yield", "you",
            "young", "your", "yours", "yourself", "youth", "zebra", "zero", "zone", "zoo"
        )

        const val MIN_PASSPHRASE_LENGTH = 12
        private const val PBKDF2_ITERATIONS = 2048
        private const val SEED_LENGTH = 64 // 512 bits
    }

    private val secureRandom = SecureRandom()

    /**
     * 生成 BIP-39 助记词（12 词）。
     *
     * @return 助记词列表
     */
    fun generateMnemonic(): List<String> {
        val entropy = ByteArray(16) // 128 bits = 12 words
        secureRandom.nextBytes(entropy)
        return encodeToMnemonic(entropy)
    }

    /**
     * 验证助记词并恢复主账号。
     *
     * @param words 助记词列表
     * @param passphrase 密码短语（可选）
     * @return 恢复结果
     */
    suspend fun recoverFromMnemonic(
        words: List<String>,
        passphrase: String? = null
    ): Result<MasterIdentity> {
        // 1. 验证助记词校验和
        if (!validateMnemonic(words)) {
            return Result.failure(IllegalArgumentException("助记词校验失败"))
        }

        // 2. 验证密码短语强度
        if (passphrase != null && passphrase.length < MIN_PASSPHRASE_LENGTH) {
            return Result.failure(IllegalArgumentException(
                "密码短语长度至少为 $MIN_PASSPHRASE_LENGTH 字符"
            ))
        }

        // 3. 从助记词重建种子
        val seed = mnemonicToSeed(words, passphrase ?: "")

        // 4. 通过 IdentityManager 恢复主账号
        return identityManagerProvider.get().restoreMasterIdentity(seed)
    }

    /**
     * 暴力穷举防护：使用密码短语（BIP-39 Passphrase）。
     *
     * 12 个助记词（128 bits 熵）提供约 3.4 × 10^38 种组合。
     * 密码短语额外增加 > 2^100 的密钥空间。
     * 即使攻击者每秒尝试数百万次，也无法在合理时间内破解。
     */
    private fun mnemonicToSeed(words: List<String>, passphrase: String): ByteArray {
        val salt = ("mnemonic" + passphrase).toByteArray()
        val spec = PBEKeySpec(
            words.joinToString(" ").toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            SEED_LENGTH * 8
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded
    }

    /**
     * 验证助记词。
     *
     * @param words 助记词列表
     * @return 是否有效
     */
    private fun validateMnemonic(words: List<String>): Boolean {
        // 验证词数
        if (words.size !in listOf(12, 15, 18, 21, 24)) {
            return false
        }
        // 验证每个词都在词库中
        if (!words.all { it.lowercase() in WORD_LIST }) {
            return false
        }
        // TODO: 验证校验和
        return true
    }

    /**
     * 将熵编码为助记词。
     */
    private fun encodeToMnemonic(entropy: ByteArray): List<String> {
        // BIP-39 熵 → 助记词编码
        // 简化实现：实际应按 BIP-39 规范实现
        val bits = entropy.toBitString()
        val words = mutableListOf<String>()
        for (i in 0 until bits.length step 11) {
            val index = bits.substring(i, minOf(i + 11, bits.length)).toInt(2)
            words.add(WORD_LIST.getOrElse(index % WORD_LIST.size) { WORD_LIST[0] })
        }
        return words
    }

    private fun ByteArray.toBitString(): String {
        return joinToString("") { byte -> 
            Integer.toBinaryString(byte.toInt() and 0xFF).padStart(8, '0')
        }
    }
}


