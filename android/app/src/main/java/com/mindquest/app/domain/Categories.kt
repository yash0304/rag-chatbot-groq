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
        Category("places", "Places", "📍"),
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
        "places" to listOf(
            "dhaba", "restaurant", "cafe", "eatery", "bakery", "homestay", "guesthouse",
            "hostel", "viewpoint", "waterfall", "beach", "temple", "fort", "museum",
            "market", "bazaar", "street", "spot", "place", "joint", "stall",
        ),
        "shopping" to listOf(
            "buy", "shop", "shopping", "cart", "order", "amazon", "flipkart", "delivery",
            "grocery", "groceries", "purchase", "mall", "store", "discount", "sale", "offer",
            "milk", "eggs", "rice", "vegetables", "fruit", "bread", "snack",
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
            "father", "sister", "brother", "wife", "husband", "gift", "invite",
            "message", "reply", "meet",
        ),
        // Cooking only. Groceries are Shopping and restaurants are Places — lumping all
        // three together was what made "Gokul Dhaba, must visit" look like a food errand.
        "food" to listOf(
            "recipe", "cook", "cooking", "bake", "marinate", "boil", "fry", "ingredient",
            "portion", "leftovers", "meal prep",
        ),
        "admin" to listOf(
            "aadhaar", "pan", "licence", "license", "renew", "renewal", "form", "apply",
            "application", "document", "certificate", "registration", "verify", "kyc",
            "police", "government", "office work", "submit", "deadline extension",
        ),
    )

    /**
     * Words that mark a note as a recommendation rather than a plan. These are what
     * separate "Gokul Dhaba Colaba — must visit" (a place worth remembering) from
     * "book the resort for the 14th" (a trip to arrange), even though both name a venue.
     */
    private val recommendation = listOf(
        "must", "visit", "worth", "try", "recommend", "recommended", "best", "favourite",
        "favorite", "loved", "amazing", "famous", "underrated",
    )

    /** Best-guess category id for a piece of text. Falls back to "general". */
    fun classify(text: String): String {
        val tokens = Retrieval.tokenize(text).toSet()
        if (tokens.isEmpty()) return "general"

        fun hits(words: List<String>) = words.count { keyword ->
            // Compare on stems so "flights", "booking" and "tickets" match their roots.
            val stemmed = Retrieval.tokenize(keyword)
            stemmed.isNotEmpty() && stemmed.all { it in tokens }
        }

        val scores = LinkedHashMap<String, Int>()
        for ((id, words) in keywords) {
            val score = hits(words)
            if (score > 0) scores[id] = score
        }

        // A venue named alongside a recommendation is a place worth keeping, not a journey
        // to organise. The boost is deliberately larger than one keyword so it can outweigh
        // the travel words a venue note usually also contains ("stay", "hotel").
        if (hits(recommendation) > 0 && (scores["places"] ?: 0) > 0) {
            scores["places"] = scores.getValue("places") + 2
        }

        return scores.maxByOrNull { it.value }?.key ?: "general"
    }
}
