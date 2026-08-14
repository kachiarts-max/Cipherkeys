package com.cipherkeys.app.dictionary

/**
 * Smart suggestion engine for CipherKeys.
 *
 * Combines:
 *
 * 1. Normal dictionary completions
 * 2. User-learned vocabulary
 * 3. Word-to-word context predictions
 * 4. Phrase-level predictions
 * 5. Personal usage frequency
 *
 * Everything remains local to the device.
 */
class SmartSuggestionEngine(
    private val dictionary: Dictionary,
    private val contextPredictor: ContextPredictor,
    private val phrasePredictor: PhrasePredictor
) {

    /**
     * Returns the strongest suggestions for the
     * currently typed word.
     *
     * Example:
     *
     * Previous words:
     *     "thank you"
     *
     * Current prefix:
     *     "f"
     *
     * Possible result:
     *
     *     for
     *     from
     *     ...
     */
    fun suggest(
        prefix: String,
        previousWords: List<String>,
        limit: Int = 3
    ): List<String> {

        val normalizedPrefix =
            prefix.trim().lowercase()

        val normalizedWords =
            previousWords
                .map {
                    it.trim().lowercase()
                }
                .filter {
                    it.isNotBlank()
                }

        /*
         * -----------------------------------------------------
         * 1. Dictionary candidates
         * -----------------------------------------------------
         */
        val dictionaryCandidates =
            if (normalizedPrefix.isNotBlank()) {

                dictionary.suggestCompletions(
                    normalizedPrefix,
                    limit = 10
                )

            } else {

                emptyList()
            }

        /*
         * -----------------------------------------------------
         * 2. Word-level context candidates
         * -----------------------------------------------------
         */
        val previousWord =
            normalizedWords.lastOrNull()
                ?: ""

        val contextCandidates =
            if (previousWord.isNotBlank()) {

                contextPredictor.suggestNextWords(
                    previousWord,
                    limit = 10
                )

            } else {

                emptyList()
            }

        /*
         * -----------------------------------------------------
         * 3. Phrase-level candidates
         * -----------------------------------------------------
         *
         * We check increasingly useful phrase sizes.
         *
         * Example:
         *
         * "I am going"
         *
         * checks:
         *
         * "going"
         * "am going"
         * "I am going"
         */
        val phraseCandidates =
            getPhraseCandidates(
                normalizedWords
            )

        /*
         * -----------------------------------------------------
         * 4. Combine everything
         * -----------------------------------------------------
         */
        val candidates =
            (
                phraseCandidates +
                        contextCandidates +
                        dictionaryCandidates
                )
                .map {
                    it.trim().lowercase()
                }
                .filter {
                    it.isNotBlank()
                }
                .filter {
                    normalizedPrefix.isBlank() ||
                            it.startsWith(
                                normalizedPrefix
                            )
                }
                .distinct()

        if (candidates.isEmpty()) {
            return emptyList()
        }

        /*
         * -----------------------------------------------------
         * 5. Rank candidates
         * -----------------------------------------------------
         */
        return candidates
            .map { candidate ->

                candidate to scoreCandidate(
                    candidate = candidate,
                    prefix = normalizedPrefix,
                    previousWords = normalizedWords
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
     * Backwards-compatible overload.
     *
     * This allows older CipherKeys code that only
     * provides one previous word to continue working.
     */
    fun suggest(
        prefix: String,
        previousWord: String,
        limit: Int = 3
    ): List<String> {

        val previous =
            previousWord
                .trim()
                .lowercase()

        val words =
            if (previous.isBlank()) {
                emptyList()
            } else {
                listOf(previous)
            }

        return suggest(
            prefix = prefix,
            previousWords = words,
            limit = limit
        )
    }

    /**
     * Returns phrase predictions using the most
     * recent 1–3 words.
     */
    private fun getPhraseCandidates(
        previousWords: List<String>
    ): List<String> {

        if (previousWords.isEmpty()) {
            return emptyList()
        }

        val results =
            mutableListOf<String>()

        /*
         * Prefer longer phrases because they contain
         * more context.
         */
        val maximumPhraseLength =
            minOf(
                3,
                previousWords.size
            )

        for (
            length in maximumPhraseLength downTo 1
        ) {

            val start =
                previousWords.size - length

            val phrase =
                previousWords.subList(
                    start,
                    previousWords.size
                )

            results +=
                phrasePredictor.suggestNextWords(
                    phrase,
                    limit = 10
                )
        }

        return results.distinct()
    }

    /**
     * Calculates the strength of a candidate.
     */
    private fun scoreCandidate(
        candidate: String,
        prefix: String,
        previousWords: List<String>
    ): Int {

        var score = 0

        /*
         * -----------------------------------------------------
         * Prefix relevance
         * -----------------------------------------------------
         */
        if (prefix.isNotBlank()) {

            if (
                !candidate.startsWith(
                    prefix
                )
            ) {
                return Int.MIN_VALUE
            }

            score += 100

            /*
             * Don't suggest the exact word currently
             * being typed.
             */
            if (candidate == prefix) {
                score -= 50
            }
        }

        /*
         * -----------------------------------------------------
         * Phrase relevance
         * -----------------------------------------------------
         */
        score += phraseScore(
            candidate,
            previousWords
        )

        /*
         * -----------------------------------------------------
         * Word-level context
         * -----------------------------------------------------
         */
        val previousWord =
            previousWords.lastOrNull()

        if (
            !previousWord.isNullOrBlank()
        ) {

            val contextWords =
                contextPredictor
                    .suggestNextWords(
                        previousWord,
                        limit = 20
                    )

            if (
                candidate in contextWords
            ) {
                score += 60
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

            score += 50

            score += minOf(
                dictionary.usageCount(
                    candidate
                ),
                40
            )
        }

        /*
         * -----------------------------------------------------
         * Word length
         * -----------------------------------------------------
         *
         * Small preference for shorter completions.
         */
        score -= minOf(
            candidate.length,
            20
        )

        return score
    }

    /**
     * Gives additional weight to predictions learned
     * from longer phrases.
     *
     * 3-word phrase = strongest
     * 2-word phrase = medium
     * 1-word phrase = basic context
     */
    private fun phraseScore(
        candidate: String,
        previousWords: List<String>
    ): Int {

        if (previousWords.isEmpty()) {
            return 0
        }

        var score = 0

        val maximumPhraseLength =
            minOf(
                3,
                previousWords.size
            )

        for (
            length in maximumPhraseLength downTo 1
        ) {

            val start =
                previousWords.size - length

            val phrase =
                previousWords.subList(
                    start,
                    previousWords.size
                )

            val predictions =
                phrasePredictor
                    .suggestNextWords(
                        phrase,
                        limit = 20
                    )

            if (
                candidate in predictions
            ) {

                when (length) {

                    3 -> score += 150

                    2 -> score += 110

                    1 -> score += 40
                }

                /*
                 * Longer context already provides
                 * stronger evidence, so don't keep
                 * stacking the same relationship.
                 */
                break
            }
        }

        return score
    }

    /**
     * Returns context-only predictions.
     *
     * Used after the user has typed a space and
     * hasn't started the next word yet.
     */
    fun suggestContext(
        previousWords: List<String>,
        limit: Int = 3
    ): List<String> {

        if (previousWords.isEmpty()) {
            return emptyList()
        }

        val normalizedWords =
            previousWords
                .map {
                    it.trim().lowercase()
                }
                .filter {
                    it.isNotBlank()
                }

        if (normalizedWords.isEmpty()) {
            return emptyList()
        }

        val phraseCandidates =
            getPhraseCandidates(
                normalizedWords
            )

        val previousWord =
            normalizedWords.last()

        val contextCandidates =
            contextPredictor
                .suggestNextWords(
                    previousWord,
                    limit = limit * 3
                )

        val candidates =
            (
                phraseCandidates +
                        contextCandidates
                )
                .distinct()

        return candidates
            .map { word ->

                word to contextScore(
                    word,
                    normalizedWords
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
     * Backwards-compatible context method.
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

        return suggestContext(
            previousWords = listOf(
                normalized
            ),
            limit = limit
        )
    }

    /**
     * Scores a context-only candidate.
     */
    private fun contextScore(
        word: String,
        previousWords: List<String>
    ): Int {

        var score = 100

        /*
         * Phrase intelligence.
         */
        score += phraseScore(
            word,
            previousWords
        )

        /*
         * Personal vocabulary.
         */
        if (
            dictionary.isLearnedWord(
                word
            )
        ) {

            score += 50

            score += minOf(
                dictionary.usageCount(
                    word
                ),
                40
            )
        }

        /*
         * Slight preference for shorter words.
         */
        score -= minOf(
            word.length,
            20
        )

        return score
    }
}
