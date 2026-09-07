package com.mindquest.app.domain

/**
 * Life categories for notes and documents.
 *
 * These are the buckets a person actually thinks in — travelling, shopping, money, health —
 * not the "first long word + Realm" placeholder this replaces. Classification is keyword
 * based and runs offline in microseconds; it is a first guess the user can always override,
 * never an authority. Ambiguity is resolved by weight of evidence: every matching keyword
 * scores, and the highest-scoring category wins, so one stray word can't hijack a note.
 */
object Categories {

    data class Category(val id: String, val label: String, val icon: String)

    val all = listOf(
        Category("travel", "Travelling", "🧭"),
        Category("shopping", "Shopping", "🛒"),
        Category("money", "Money", "💰"),
        Category("health", "Health", "🌿"),
        Category("work", "Work", "⚒️"),
        Category("learning", "Learning", "📚"),
        Category("home", "Home", "🏠"),
        Category("people", "People", "🫂"),
        Category("food", "Food", "🍲"),
        Category("admin", "Admin", "🗂️"),
        Category("general", "General", "📌"),
    )

    private val byId = all.associateBy { it.id }

    fun of(id: String?): Category = byId[id] ?: byId.getValue("general")

    /**
     * Keyword sets. Indian rail/air/road vocabulary is included deliberately — "Sarai
     * Rohilla", "platform", "PNR", "IRCTC" are travel words in this user's life and a
     * generic English list would miss them entirely.
     */
    private val keywords: Map<String, List<String>> = mapOf(
        "travel" to listOf(
            "travel", "trip", "tour", "flight", "airport", "terminal", "gate", "boarding",
            "train", "railway", "station", "platform", "pnr", "irctc", "coach", "berth",
            "sarai", "rohilla", "junction", "express", "ticket", "hotel", "resort", "stay",
            "checkin", "check-in", "checkout", "itinerary", "visa", "passport", "luggage",
            "baggage", "taxi", "cab", "uber", "ola", "bus", "metro", "route", "departure",
            "arrival", "vacation", "holiday", "sightseeing", "safari", "trek", "camp",
        ),
        "shopping" to listOf(
            "buy", "shop", "shopping", "cart", "order", "amazon", "flipkart", "delivery",
            "grocery", "groceries", "purchase", "mall", "store", "discount", "sale", "offer",
            "coupon", "return", "refund", "size", "brand",
        ),
        "money" to listOf(
            "money", "salary", "invoice", "bill", "payment", "paid", "pay", "emi", "loan",
            "bank", "account", "upi", "credit", "debit", "tax", "gst", "budget", "expense",
            "invest", "sip", "mutual", "stock", "insurance", "premium", "rent", "refundable",
        ),
        "health" to listOf(
            "health", "doctor", "appointment", "clinic", "hospital", "medicine", "tablet",
            "prescription", "dose", "gym", "workout", "exercise", "yoga", "sleep", "diet",
            "weight", "breathing", "anxiety", "stress", "calm", "therapy", "checkup", "dental",
        ),
        "work" to listOf(
            "work", "office", "meeting", "client", "project", "deadline", "sprint", "standup",
            "review", "manager", "team", "presentation", "report", "email", "interview",
            "resume", "appraisal", "deliverable", "release", "deploy",
        ),
        "learning" to listOf(
            "learn", "learning", "study", "course", "lecture", "tutorial", "chapter", "exam",
            "syllabus", "notes", "research", "paper", "book", "reading", "practice", "revision",
        ),
        "home" to listOf(
            "home", "house", "repair", "plumber", "electrician", "boiler", "gauge", "leak",
            "clean", "laundry", "maintenance", "furniture", "kitchen", "garden", "society",
            "landlord", "electricity", "water", "gas", "wifi", "broadband",
        ),
        "people" to listOf(
            "call", "birthday", "anniversary", "wedding", "friend", "family", "mother",
            "father", "sister", "brother", "wife", "husband", "gift", "visit", "invite",
            "message", "reply", "meet",
        ),
        "food" to listOf(
            "food", "eat", "lunch", "dinner", "breakfast", "recipe", "cook", "restaurant",
            "menu", "milk", "eggs", "rice", "vegetables", "fruit", "snack", "tea", "coffee",
            "order food", "swiggy", "zomato",
        ),
        "admin" to listOf(
            "aadhaar", "pan", "licence", "license", "renew", "renewal", "form", "apply",
            "application", "document", "certificate", "registration", "verify", "kyc",
            "police", "government", "office work", "submit", "deadline extension",
        ),
    )

    /** Best-guess category id for a piece of text. Falls back to "general". */
    fun classify(text: String): String {
        val tokens = Retrieval.tokenize(text).toSet()
        if (tokens.isEmpty()) return "general"

        var bestId = "general"
        var bestScore = 0
        for ((id, words) in keywords) {
            // Compare on stems so "flights", "booking" and "tickets" match their roots.
            val score = words.count { keyword ->
                val stemmed = Retrieval.tokenize(keyword)
                stemmed.isNotEmpty() && stemmed.all { it in tokens }
            }
            if (score > bestScore) { bestScore = score; bestId = id }
        }
        return bestId
    }
}
