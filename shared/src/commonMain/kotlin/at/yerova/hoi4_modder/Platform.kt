package at.yerova.hoi4_modder

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform