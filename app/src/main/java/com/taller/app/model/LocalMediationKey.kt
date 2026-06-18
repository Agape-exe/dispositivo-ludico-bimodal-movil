package com.taller.app.model

enum class LocalMediationKey(val displayName: String) {
    NONE("Ninguna / General"),
    ANIMAL_DOG_SOUND("Sonido del perro"),
    ANIMAL_DOMESTIC("Animal doméstico"),
    ANIMAL_CAT_SOUND("Sonido del gato"),
    ANIMAL_FARM("Animal de granja");

    companion object {
        fun fromKey(key: String?): LocalMediationKey =
            if (key.isNullOrBlank()) NONE
            else entries.find { it.name == key } ?: NONE
    }
}
