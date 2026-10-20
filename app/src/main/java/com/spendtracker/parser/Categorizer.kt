package com.spendtracker.parser

import com.spendtracker.model.Category
import com.spendtracker.model.TransactionType

/**
 * Rule based categorization. The payee name / VPA is matched against merchant keywords first;
 * when nothing matches, a few strong hints in the SMS text (NACH/EMI, ATM, NEFT...) are used.
 * User corrections are stored separately and always win over these defaults.
 */
object Categorizer {

    private val IC = RegexOption.IGNORE_CASE

    private fun words(vararg patterns: String) = Regex("""\b(?:${patterns.joinToString("|")})""", IC)

    // Order matters: the first category whose keywords match the payee wins.
    private val MERCHANT_RULES: List<Pair<Category, Regex>> = listOf(
        Category.RENT to words(
            """rent\b""", """rental\b""", "house ?rent", "room ?rent", """pg\b""", "nobroker", "nestaway", "landlord",
        ),
        Category.EMI to words(
            """emi\b""", "loan", "bajaj ?fin", "bajaj ?finserv", "home ?credit", "hdb ?fin", "tata ?capital",
            "kreditbee", "money ?view", """fibe\b""", """cashe\b""", "muthoot", "manappuram", """iifl\b""",
            "cholamandalam", "shriram", "hero ?fincorp", "tvs ?credit", """l ?& ?t ?fin""", """ltfs\b""",
            "mahindra ?fin", "piramal", """navi\b""", "lazypay", "zestmoney", """nach\b""",
        ),
        Category.CARD_BILL to words(
            "credit ?card", """cc ?(?:bill|payment)\b""", """card ?(?:bill|payment)""", """cred\b""", "cred ?club",
            """sbi ?card""", """amex\b""",
        ),
        Category.GROCERIES to words(
            "bigbasket", "big ?basket", "blinkit", "grofers", """zepto""", "instamart", """d ?mart\b""",
            "avenue ?supermarts", "jiomart", "reliance ?(?:fresh|smart|retail)", "smart ?bazaar", "more ?retail",
            "spencer", "nature'?s ?basket", "star ?bazaar", "ratnadeep", "kirana", "grocer", "supermarket",
            "super ?market", "hypermarket", "provision", "vegetable", "veggies", """fruits?\b""", "dairy",
            """milk\b""", """amul\b""", "mother ?dairy", "country ?delight", "milkbasket", """bb ?daily""", "dunzo",
            "licious", "fresh ?to ?home", """\w*mart\b""", "general ?stores?",
        ),
        Category.FUEL to words(
            "petrol", "diesel", """fuels?\b""", "filling ?station", "service ?station", """hpcl\b""", """bpcl\b""",
            "bharat ?petroleum", "hindustan ?petroleum", """iocl?\b""", "indian ?oil", "indianoil", """shell\b""",
            "nayara", "essar ?oil", "jio[ -]?bp", "reliance ?bp", """cng\b""", "gas ?station", "petro",
        ),
        Category.FOOD to words(
            "swiggy", "zomato", "eatsure", "domino", "pizza", "mcdonald", """kfc\b""", "burger", """subway\b""",
            "starbucks", """cafe""", "café", "coffee", """chai""", """tea\b""", "restaurant", """resto\b""", "dhaba",
            """hotel""", "bakery", "bakers", "sweets", "biryani", "kitchen", """foods?\b""", "eatery", "canteen",
            """mess\b""", """juice""", "haldiram", "barbeque", """bbq\b""", "chaayos", "faasos", "behrouz",
            """box8\b""", "ice ?cream", "baskin",
        ),
        Category.ENTERTAINMENT to words(
            "netflix", "prime ?video", "hotstar", """disney""", "jio ?cinema", "sonyliv", """zee5\b""", "spotify",
            "gaana", """wynk\b""", "youtube", "apple ?music", "bookmyshow", """pvr\b""", """inox\b""", "cinepolis",
            "cinema", """movies?\b""", "multiplex", """steam\b""", "playstation", """xbox\b""", "dream11",
        ),
        Category.BILLS to words(
            "electricity", """bescom\b""", "msedcl", "mahadiscom", """tneb\b""", "tangedco", """bses\b""",
            "tata ?power", "adani ?(?:electricity|energy)", "torrent ?power", """cesc\b""", """uppcl\b""",
            """pspcl\b""", """kseb\b""", "wbsedcl", "water ?(?:bill|board|supply)", "jal ?board", "gas ?bill",
            "piped ?gas", "mahanagar ?gas", """igl\b""", "indraprastha ?gas", """lpg\b""", "indane", "bharat ?gas",
            """hp ?gas\b""", "broadband", "fibernet", "hathway", "excitel", "tata ?(?:play|sky)", "dish ?tv",
            """d2h\b""", "airtel", """jio\b""", "reliance ?jio", "vodafone", """idea\b""", """vi\b""", """bsnl\b""",
            """mtnl\b""", "recharge", "postpaid", "prepaid", """dth\b""", "bill ?pay", """bbps\b""", "insurance",
            """lic\b""", "life ?insurance", "maintenance", "society", "municipal", "property ?tax",
        ),
        Category.TRAVEL to words(
            """uber\b""", """ola\b""", "olacabs", "ola ?cabs", "rapido", "blusmart", """irctc\b""", "railway",
            """metro\b""", """dmrc\b""", "bmrcl", "redbus", "abhibus", "makemytrip", """mmt\b""", "goibibo",
            """yatra\b""", "cleartrip", """ixigo\b""", "easemytrip", """indigo\b""", "interglobe", "air ?india",
            "vistara", "spicejet", """akasa\b""", "airasia", "airline", "airport", "fastag", """toll\b""",
            "parking", """nhai\b""", """yulu\b""", "zoomcar", """travels?\b""", """tours?\b""", """ksrtc\b""",
            """msrtc\b""", """tsrtc\b""", """apsrtc\b""", """cabs?\b""", """taxi\b""",
        ),
        Category.HEALTH to words(
            "pharma", "medical", "medicine", "medplus", "apollo", """1mg\b""", "netmeds", "pharmeasy", "practo",
            "hospital", "clinic", "diagnostic", """labs?\b""", "thyrocare", "lal ?path", "metropolis", "dental",
            "dentist", "doctor", """dr\.? """, "healthcare", "chemist", "druggist", "cult\\.?fit", """gym\b""",
            "fitness",
        ),
        Category.INVESTMENTS to words(
            "zerodha", """groww\b""", "upstox", "angel ?one", "angel ?broking", "kuvera", "paytm ?money",
            "smallcase", "indmoney", """etmoney\b""", "mutual ?funds?", """mf\b""", """sip\b""", """nps\b""",
            """ppf\b""", "recurring ?deposit", "fixed ?deposit", """iccl\b""", """nsccl\b""", """cams\b""",
            "kfintech", "safegold", "augmont", "digital ?gold", "mmtc",
        ),
        Category.SHOPPING to words(
            "amazon", """amzn\b""", "flipkart", "myntra", """ajio\b""", "meesho", "nykaa", "snapdeal",
            "tata ?cliq", """croma\b""", "reliance ?digital", "vijay ?sales", "decathlon", """ikea\b""",
            "lifestyle", "westside", "pantaloons", "max ?fashion", "shoppers ?stop", """zudio\b""",
            """h ?& ?m\b""", """uniqlo\b""", """zara\b""", "lenskart", "tanishq", "firstcry", "pepperfry",
            "urban ?ladder", "electronics", "fashion", "garments", "textiles", "footwear", """bata\b""",
            "jewel", """mall\b""",
        ),
        Category.CASH to words("""atm\b""", "cash ?withdrawal", """cash ?wdl\b"""),
    )

    private val BODY_EMI = Regex("""\b(emi|nach|ecs|loan|ach[ -]?d(?:r|ebit)?)\b""", IC)
    private val BODY_CASH = Regex("""\b(atm|cash withdrawal|cash wdl|withdrawn at|atm wdl)\b""", IC)
    private val BODY_RENT = Regex("""\brent\b""", IC)
    private val BODY_CARD_BILL = Regex(
        """\b(?:credit card|card) (?:bill|payment|dues)\b|\bpayment (?:towards|to|for|of) (?:your )?(?:credit )?card\b|\btowards (?:your )?(?:credit )?card\b""",
        IC,
    )
    private val BODY_BANK_TRANSFER = Regex("""\b(neft|imps|rtgs|p2a|p2p)\b""", IC)

    private val SALARY = Regex("""\b(salary|sal|payroll|salaries|stipend)\b""", IC)
    private val REFUND = Regex("""\b(refund|refunded|reversal|reversed|cashback|cash back)\b""", IC)
    private val COMPANY = Regex(
        """\b(pvt|private|ltd|limited|llp|inc|corp|corporation|technologies|technology|solutions|services|consulting|consultancy|systems|software|infotech|labs)\b""",
        IC,
    )
    private val BANK_RAIL = Regex("""\b(neft|imps|rtgs)\b""", IC)
    private const val SALARY_LIKE_MIN_PAISE = 10_000_00L

    fun categorize(parsed: ParsedSms, body: String): Category {
        val payee = listOfNotNull(parsed.counterparty, parsed.vpa?.substringBefore('@')).joinToString(" ")

        if (parsed.type == TransactionType.CREDIT) {
            return when {
                SALARY.containsMatchIn(body) || SALARY.containsMatchIn(payee) -> Category.SALARY
                REFUND.containsMatchIn(body) -> Category.REFUND
                // A sizeable bank transfer from a company is almost always salary.
                parsed.amountPaise >= SALARY_LIKE_MIN_PAISE && BANK_RAIL.containsMatchIn(body) &&
                    COMPANY.containsMatchIn(payee) -> Category.SALARY
                else -> Category.OTHER_INCOME
            }
        }

        if (payee.isNotBlank()) {
            MERCHANT_RULES.firstOrNull { it.second.containsMatchIn(payee) }?.let { return it.first }
        }
        return when {
            BODY_EMI.containsMatchIn(body) -> Category.EMI
            BODY_CASH.containsMatchIn(body) -> Category.CASH
            BODY_RENT.containsMatchIn(body) -> Category.RENT
            BODY_CARD_BILL.containsMatchIn(body) -> Category.CARD_BILL
            BODY_BANK_TRANSFER.containsMatchIn(body) -> Category.TRANSFERS
            else -> Category.DAILY
        }
    }
}
