package com.cipherkeys.app.dictionary

/**
 * Smart suggestion engine for CipherKeys.
 *
 * Combines:
 *
 * 1. Normal dictionary completions
 * 2. User-learned vocabulary
 * 3. Context predictions
 * 4. Personal usage frequency
 *
 * The engine ranks candidates and returns the
 * strongest suggestions for the keyboard.
 *
 * Everything remains local to the device.
 */
class SmartSuggestionEngine(
    private val dictionary: Dictionary,
    private val contextPredictor: ContextPredictor
) {

    /**
     * Returns the best suggestions for the current
     * word prefix.
     *
     * [prefix]
     *      The characters currently being typed.
     *
     * [previousWord]
     *      The most recently completed word.
     */
    fun suggest(
        prefix: String,
        previousWord: String,
        limit: Int = 3
    ): List<String> {

        val normalizedPrefix =
            prefix.trim().lowercase()

        val normalizedPrevious =
            previousWord.trim().lowercase()

        /*
         * -----------------------------------------------------
         * 1. Dictionary completions
         * -----------------------------------------------------
         *
         * Ask the dictionary for more candidates than we
         * ultimately display.
         */
        val dictionaryCandidates =
            if (normalizedPrefix.isNotBlank()) {

                dictionary
                    .suggestCompletions(
                        normalizedPrefix,
                        limit = 10
                    )

            } else {

                emptyList()
            }

        /*
         * -----------------------------------------------------
         * 2. Context predictions
         * -----------------------------------------------------
         *
         * These represent words commonly used after the
         * previous word.
         */
        val contextCandidates =
            if (normalizedPrevious.isNotBlank()) {

                contextPredictor
                    .suggestNextWords(
                        normalizedPrevious,
                        limit = 10
                    )

            } else {

                emptyList()
            }

        /*
         * -----------------------------------------------------
         * 3. Combine candidates
         * -----------------------------------------------------
         */
        val candidates =
            (
                contextCandidates +
                        dictionaryCandidates
                )
                .map {
                    it.trim().lowercase()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (candidates.isEmpty()) {
            return emptyList()
        }

        /*
         * -----------------------------------------------------
         * 4. Rank candidates
         * -----------------------------------------------------
         *
         * Higher score = stronger suggestion.
         */
        return candidates
            .map { candidate ->

                candidate to
                        scoreCandidate(
                            candidate,
                            normalizedPrefix,
                            normalizedPrevious
                        )
            }
            .sortedByDescending {
                it.second
            }
            .take(limit)
            .map {
                it.first
            }
    }

    /**
     * Calculate a suggestion score.
     *
     * The scoring system intentionally favors:
     *
     * 1. Exact prefix matches
     * 2. Context predictions
     * 3. Personal vocabulary
     * 4. Frequently used personal words
     * 5. Shorter completions
     */
    private fun scoreCandidate(
        candidate: String,
        prefix: String,
        previousWord: String
    ): Int {

        var score = 0

        /*
         * -----------------------------------------------------
         * Prefix relevance
         * -----------------------------------------------------
         */
        if (prefix.isNotBlank()) {

            if (
                candidate.startsWith(prefix)
            ) {
                score += 100
            } else {
                /*
                 * A context prediction that doesn't match
                 * the characters currently typed should not
                 * normally appear.
                 */
                return Int.MIN_VALUE
            }

            /*
             * Exact prefix is not itself a useful suggestion.
             */
            if (candidate == prefix) {
                score -= 50
            }
        }

        /*
         * -----------------------------------------------------
         * Context relevance
         * -----------------------------------------------------
         */
        if (previousWord.isNotBlank()) {

            val contextWords =
                contextPredictor
                    .suggestNextWords(
                        previousWord,
                        limit = 20
                    )

            if (
                contextWords.contains(
                    candidate
                )
            ) {
                score += 80
            }
        }

        /*
         * -----------------------------------------------------
         * Personal vocabulary
         * -----------------------------------------------------
         */
        if (
            dictionary.isLearnedWord(
                candidate
            )
        ) {

            score += 60

            /*
             * Frequently used personal words get an
             * additional advantage.
             */
            val usage =
                dictionary.usageCount(
                    candidate
                )

            score += minOf(
                usage,
                40
            )
        }

        /*
         * -----------------------------------------------------
         * Word length
         * -----------------------------------------------------
         *
         * Slight preference for shorter completions.
         */
        score -= minOf(
            candidate.length,
            20
        )

        return score
    }

    /**
     * Returns context-only predictions.
     *
     * Used when the user has just typed a space and
     * hasn't started the next word yet.
     */
    fun suggestContext(
        previousWord: String,
        limit: Int = 3
    ): List<String> {

        val normalized =
            previousWord
                .trim()
                .lowercase()

        if (normalized.isBlank()) {
            return emptyList()
        }

        return contextPredictor
            .suggestNextWords(
                normalized,
                limit = limit * 3
            )
            .distinct()
            .map {
                it to contextScore(it)
            }
            .sortedByDescending {
                it.second
            }
            .take(limit)
            .map {
                it.first
            }
    }

    /**
     * Score a context-only prediction.
     */
    private fun contextScore(
        word: String
    ): Int {

        var score = 100

        if (
            dictionary.isLearnedWord(
                word
            )
        ) {

            score += 50

            score += minOf(
                dictionary.usageCount(word),
                40
            )
        }

        score -= minOf(
            word.length,
            20
        )

        return score
    }
}
