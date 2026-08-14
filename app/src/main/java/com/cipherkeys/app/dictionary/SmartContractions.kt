package com.cipherkeys.app.dictionary

/**
 * Smart contraction engine for CipherKeys.
 *
 * Converts common words typed without apostrophes into their
 * natural English contractions.
 *
 * Examples:
 *
 * dont     -> don't
 * cant     -> can't
 * wouldnt  -> wouldn't
 * im       -> i'm
 * youre    -> you're
 * theyre   -> they're
 *
 * Everything runs locally on the device.
 */
object SmartContractions {

    private val contractions = mapOf(

        // "not" contractions
        "dont" to "don't",
        "doesnt" to "doesn't",
        "didnt" to "didn't",
        "cant" to "can't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "wont" to "won't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "havent" to "haven't",
        "hasnt" to "hasn't",
        "hadnt" to "hadn't",
        "mustnt" to "mustn't",
        "mightnt" to "mightn't",
        "neednt" to "needn't",

        // "am / are / is" contractions
        "im" to "i'm",
        "youre" to "you're",
        "hes" to "he's",
        "shes" to "she's",
        "its" to "it's",
        "were" to "we're",
        "theyre" to "they're",

        // "have" contractions
        "ive" to "i've",
        "youve" to "you've",
        "weve" to "we've",
        "theyve" to "they've",

        // "would" contractions
        "id" to "i'd",
        "youd" to "you'd",
        "hed" to "he'd",
        "shed" to "she'd",
        "wed" to "we'd",
        "theyd" to "they'd",

        // "will" contractions
        "ill" to "i'll",
        "youll" to "you'll",
        "hell" to "he'll",
        "shell" to "she'll",
        "well" to "we'll",
        "theyll" to "they'll",

        // Other common contractions
        "thats" to "that's",
        "theres" to "there's",
        "heres" to "here's",
        "whats" to "what's",
        "whos" to "who's",
        "lets" to "let's"
    )

    /**
     * Returns the natural contraction for a word.
     *
     * Example:
     *
     * SmartContractions.convert("dont")
     *
     * returns:
     *
     * "don't"
     */
    fun convert(word: String): String? {

        val normalized =
            word.trim().lowercase()

        return contractions[normalized]
    }

    /**
     * Returns true if the word has a known contraction.
     */
    fun hasContraction(word: String): Boolean {

        val normalized =
            word.trim().lowercase()

        return contractions.containsKey(
            normalized
        )
    }

    /**
     * Returns the contraction when available,
     * otherwise returns the original word.
     */
    fun convertOrOriginal(
        word: String
    ): String {

        return convert(word)
            ?: word
    }

    /**
     * Returns all supported contractions.
     *
     * Useful later for dictionary suggestions.
     */
    fun all(): List<String> {

        return contractions.values.toList()
    }

    /**
     * Preserves capitalization when possible.
     *
     * Examples:
     *
     * dont  -> don't
     * Dont  -> Don't
     * DONT  -> DON'T
     * im    -> i'm
     * Im    -> I'm
     * IM    -> I'M
     */
    fun convertPreservingCase(
        word: String
    ): String? {

        val converted =
            convert(word)
                ?: return null

        return when {

            word.all {
                it.isUpperCase() ||
                        !it.isLetter()
            } -> {
                converted.uppercase()
            }

            word.firstOrNull()
                ?.isUpperCase() == true -> {

                converted.replaceFirstChar {
                    it.uppercase()
                }

            }

            else -> {
                converted
            }
        }
    }
}
