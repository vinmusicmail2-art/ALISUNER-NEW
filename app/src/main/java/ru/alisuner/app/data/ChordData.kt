package ru.alisuner.app.data

import kotlin.math.pow

/**
 * Аппликатура аккорда на грифе гитары.
 *
 * @param name       Латинское название (например "C", "Am")
 * @param displayName Русское название (например "До мажор", "Ля минор")
 * @param position   Позиция на грифе (1 = первая позиция, 2 = вторая позиция)
 * @param fingers    Для каждой струны: лад (0=открытая, -1=не играть).
 *                   Индексы: 0=E4 (1-я тонкая), 1=B3, 2=G3, 3=D3, 4=A2, 5=E2 (6-я толстая)
 * @param barreFret  Если баррэ — номер лада; 0 = нет баррэ
 */
data class ChordFingering(
    val name: String,
    val displayName: String,
    val position: Int = 1,
    val fingers: List<Int>,
    val barreFret: Int = 0
) {
    /**
     * Вычисляет частоты звучащих нот аккорда для озвучивания.
     * Стандартный строй: E4=329.63, B3=246.94, G3=196.00, D3=146.83, A2=110.00, E2=82.41
     */
    fun getFrequencies(): List<Double> {
        val openStringFreqs = listOf(329.63, 246.94, 196.00, 146.83, 110.00, 82.41)
        val freqs = mutableListOf<Double>()
        for (i in fingers.indices) {
            val fret = fingers[i]
            if (fret >= 0) {
                freqs.add(openStringFreqs[i] * 2.0.pow(fret / 12.0))
            }
        }
        return freqs
    }
}

/**
 * Полная библиотека аккордов для гитары.
 *
 * Все 12 тональностей, мажор + минор, 1-я и 2-я позиция.
 * Подписи: латиницей и (по-русски).
 */
object ChordLibrary {

    // ───────────────────── БАЗА АККОРДОВ ─────────────────────
    // fingers: [E4, B3, G3, D3, A2, E2], -1 = не играть, 0 = открытая

    private val chordDatabase: List<ChordFingering> = listOf(

        // ═══════════════════ C — До ═══════════════════
        ChordFingering("C", "До мажор (C)", 1,
            listOf(0, 1, 0, 2, 3, -1)),
        ChordFingering("C", "До мажор (C)", 2,
            listOf(3, 5, 5, 5, 3, -1), barreFret = 3),
        ChordFingering("Cm", "До минор (Cm)", 1,
            listOf(3, 4, 5, 5, 3, -1), barreFret = 3),
        ChordFingering("Cm", "До минор (Cm)", 2,
            listOf(8, 8, 8, 10, 10, 8), barreFret = 8),

        // ═══════════════════ C# / Db — До-диез ═══════════════════
        ChordFingering("C#", "До-диез мажор (C#)", 1,
            listOf(4, 6, 6, 6, 4, -1), barreFret = 4),
        ChordFingering("C#", "До-диез мажор (C#)", 2,
            listOf(9, 9, 10, 11, 11, 9), barreFret = 9),
        ChordFingering("C#m", "До-диез минор (C#m)", 1,
            listOf(4, 5, 6, 6, 4, -1), barreFret = 4),
        ChordFingering("C#m", "До-диез минор (C#m)", 2,
            listOf(9, 9, 9, 11, 11, 9), barreFret = 9),

        // ═══════════════════ D — Ре ═══════════════════
        ChordFingering("D", "Ре мажор (D)", 1,
            listOf(2, 3, 2, 0, -1, -1)),
        ChordFingering("D", "Ре мажор (D)", 2,
            listOf(5, 7, 7, 7, 5, -1), barreFret = 5),
        ChordFingering("Dm", "Ре минор (Dm)", 1,
            listOf(1, 3, 2, 0, -1, -1)),
        ChordFingering("Dm", "Ре минор (Dm)", 2,
            listOf(5, 6, 7, 7, 5, -1), barreFret = 5),

        // ═══════════════════ D# / Eb — Ре-диез ═══════════════════
        ChordFingering("D#", "Ре-диез мажор (D#)", 1,
            listOf(6, 8, 8, 8, 6, -1), barreFret = 6),
        ChordFingering("D#", "Ре-диез мажор (D#)", 2,
            listOf(11, 11, 12, 13, 13, 11), barreFret = 11),
        ChordFingering("D#m", "Ре-диез минор (D#m)", 1,
            listOf(6, 7, 8, 8, 6, -1), barreFret = 6),
        ChordFingering("D#m", "Ре-диез минор (D#m)", 2,
            listOf(11, 11, 11, 13, 13, 11), barreFret = 11),

        // ═══════════════════ E — Ми ═══════════════════
        ChordFingering("E", "Ми мажор (E)", 1,
            listOf(0, 0, 1, 2, 2, 0)),
        ChordFingering("E", "Ми мажор (E)", 2,
            listOf(7, 9, 9, 9, 7, -1), barreFret = 7),
        ChordFingering("Em", "Ми минор (Em)", 1,
            listOf(0, 0, 0, 2, 2, 0)),
        ChordFingering("Em", "Ми минор (Em)", 2,
            listOf(7, 8, 9, 9, 7, -1), barreFret = 7),

        // ═══════════════════ F — Фа ═══════════════════
        ChordFingering("F", "Фа мажор (F)", 1,
            listOf(1, 1, 2, 3, 3, 1), barreFret = 1),
        ChordFingering("F", "Фа мажор (F)", 2,
            listOf(8, 10, 10, 10, 8, -1), barreFret = 8),
        ChordFingering("Fm", "Фа минор (Fm)", 1,
            listOf(1, 1, 1, 3, 3, 1), barreFret = 1),
        ChordFingering("Fm", "Фа минор (Fm)", 2,
            listOf(8, 9, 10, 10, 8, -1), barreFret = 8),

        // ═══════════════════ F# / Gb — Фа-диез ═══════════════════
        ChordFingering("F#", "Фа-диез мажор (F#)", 1,
            listOf(2, 2, 3, 4, 4, 2), barreFret = 2),
        ChordFingering("F#", "Фа-диез мажор (F#)", 2,
            listOf(9, 11, 11, 11, 9, -1), barreFret = 9),
        ChordFingering("F#m", "Фа-диез минор (F#m)", 1,
            listOf(2, 2, 2, 4, 4, 2), barreFret = 2),
        ChordFingering("F#m", "Фа-диез минор (F#m)", 2,
            listOf(9, 10, 11, 11, 9, -1), barreFret = 9),

        // ═══════════════════ G — Соль ═══════════════════
        ChordFingering("G", "Соль мажор (G)", 1,
            listOf(3, 0, 0, 0, 2, 3)),
        ChordFingering("G", "Соль мажор (G)", 2,
            listOf(3, 3, 4, 5, 5, 3), barreFret = 3),
        ChordFingering("Gm", "Соль минор (Gm)", 1,
            listOf(3, 3, 3, 5, 5, 3), barreFret = 3),
        ChordFingering("Gm", "Соль минор (Gm)", 2,
            listOf(10, 11, 12, 12, 10, -1), barreFret = 10),

        // ═══════════════════ G# / Ab — Соль-диез ═══════════════════
        ChordFingering("G#", "Соль-диез мажор (G#)", 1,
            listOf(4, 4, 5, 6, 6, 4), barreFret = 4),
        ChordFingering("G#", "Соль-диез мажор (G#)", 2,
            listOf(11, 13, 13, 13, 11, -1), barreFret = 11),
        ChordFingering("G#m", "Соль-диез минор (G#m)", 1,
            listOf(4, 4, 4, 6, 6, 4), barreFret = 4),
        ChordFingering("G#m", "Соль-диез минор (G#m)", 2,
            listOf(11, 12, 13, 13, 11, -1), barreFret = 11),

        // ═══════════════════ A — Ля ═══════════════════
        ChordFingering("A", "Ля мажор (A)", 1,
            listOf(0, 2, 2, 2, 0, -1)),
        ChordFingering("A", "Ля мажор (A)", 2,
            listOf(5, 5, 6, 7, 7, 5), barreFret = 5),
        ChordFingering("Am", "Ля минор (Am)", 1,
            listOf(0, 1, 2, 2, 0, -1)),
        ChordFingering("Am", "Ля минор (Am)", 2,
            listOf(5, 5, 5, 7, 7, 5), barreFret = 5),

        // ═══════════════════ A# / Bb — Ля-диез ═══════════════════
        ChordFingering("A#", "Ля-диез мажор (A#)", 1,
            listOf(1, 3, 3, 3, 1, -1), barreFret = 1),
        ChordFingering("A#", "Ля-диез мажор (A#)", 2,
            listOf(6, 6, 7, 8, 8, 6), barreFret = 6),
        ChordFingering("A#m", "Ля-диез минор (A#m)", 1,
            listOf(1, 2, 3, 3, 1, -1), barreFret = 1),
        ChordFingering("A#m", "Ля-диез минор (A#m)", 2,
            listOf(6, 6, 6, 8, 8, 6), barreFret = 6),

        // ═══════════════════ B — Си ═══════════════════
        ChordFingering("B", "Си мажор (B)", 1,
            listOf(2, 4, 4, 4, 2, -1), barreFret = 2),
        ChordFingering("B", "Си мажор (B)", 2,
            listOf(7, 7, 8, 9, 9, 7), barreFret = 7),
        ChordFingering("Bm", "Си минор (Bm)", 1,
            listOf(2, 3, 4, 4, 2, -1), barreFret = 2),
        ChordFingering("Bm", "Си минор (Bm)", 2,
            listOf(7, 7, 7, 9, 9, 7), barreFret = 7),

        // ══════════════════════════════════════════════════════════════
        // СЕПТАККОРДЫ (7, maj7, m7)
        // ══════════════════════════════════════════════════════════════

        // --- C7, Cmaj7, Cm7 ---
        ChordFingering("C7", "До доминантсептаккорд (C7)", 1,
            listOf(0, 1, 3, 2, 3, -1)),
        ChordFingering("Cmaj7", "До большой мажорный (Cmaj7)", 1,
            listOf(0, 0, 0, 2, 3, -1)),
        ChordFingering("Cm7", "До минорный септаккорд (Cm7)", 1,
            listOf(3, 4, 3, 5, 3, -1), barreFret = 3),

        // --- C#7, C#maj7, C#m7 ---
        ChordFingering("C#7", "До-диез доминантсептаккорд (C#7)", 1,
            listOf(4, 6, 4, 6, 4, -1), barreFret = 4),
        ChordFingering("C#maj7", "До-диез большой мажорный (C#maj7)", 1,
            listOf(4, 5, 6, 6, 4, -1), barreFret = 4),
        ChordFingering("C#m7", "До-диез минорный септаккорд (C#m7)", 1,
            listOf(4, 5, 4, 6, 4, -1), barreFret = 4),

        // --- D7, Dmaj7, Dm7 ---
        ChordFingering("D7", "Ре доминантсептаккорд (D7)", 1,
            listOf(2, 1, 2, 0, -1, -1)),
        ChordFingering("Dmaj7", "Ре большой мажорный (Dmaj7)", 1,
            listOf(2, 2, 2, 0, -1, -1)),
        ChordFingering("Dm7", "Ре минорный септаккорд (Dm7)", 1,
            listOf(1, 1, 2, 0, -1, -1)),

        // --- D#7, D#maj7, D#m7 ---
        ChordFingering("D#7", "Ре-диез доминантсептаккорд (D#7)", 1,
            listOf(6, 8, 6, 8, 6, -1), barreFret = 6),
        ChordFingering("D#maj7", "Ре-диез большой мажорный (D#maj7)", 1,
            listOf(6, 7, 8, 8, 6, -1), barreFret = 6),
        ChordFingering("D#m7", "Ре-диез минорный септаккорд (D#m7)", 1,
            listOf(6, 7, 6, 8, 6, -1), barreFret = 6),

        // --- E7, Emaj7, Em7 ---
        ChordFingering("E7", "Ми доминантсептаккорд (E7)", 1,
            listOf(0, 0, 1, 0, 2, 0)),
        ChordFingering("Emaj7", "Ми большой мажорный (Emaj7)", 1,
            listOf(0, 0, 1, 1, 2, 0)),
        ChordFingering("Em7", "Ми минорный септаккорд (Em7)", 1,
            listOf(0, 0, 0, 0, 2, 0)),

        // --- F7, Fmaj7, Fm7 ---
        ChordFingering("F7", "Фа доминантсептаккорд (F7)", 1,
            listOf(1, 1, 2, 1, 3, 1), barreFret = 1),
        ChordFingering("Fmaj7", "Фа большой мажорный (Fmaj7)", 1,
            listOf(0, 1, 2, 3, -1, -1)),
        ChordFingering("Fm7", "Фа минорный септаккорд (Fm7)", 1,
            listOf(1, 1, 1, 1, 3, 1), barreFret = 1),

        // --- F#7, F#maj7, F#m7 ---
        ChordFingering("F#7", "Фа-диез доминантсептаккорд (F#7)", 1,
            listOf(2, 2, 3, 2, 4, 2), barreFret = 2),
        ChordFingering("F#maj7", "Фа-диез большой мажорный (F#maj7)", 1,
            listOf(2, 2, 3, 3, 4, 2), barreFret = 2),
        ChordFingering("F#m7", "Фа-диез минорный септаккорд (F#m7)", 1,
            listOf(2, 2, 2, 2, 4, 2), barreFret = 2),

        // --- G7, Gmaj7, Gm7 ---
        ChordFingering("G7", "Соль доминантсептаккорд (G7)", 1,
            listOf(1, 0, 0, 0, 2, 3)),
        ChordFingering("Gmaj7", "Соль большой мажорный (Gmaj7)", 1,
            listOf(2, 0, 0, 0, 2, 3)),
        ChordFingering("Gm7", "Соль минорный септаккорд (Gm7)", 1,
            listOf(3, 3, 3, 3, 5, 3), barreFret = 3),

        // --- G#7, G#maj7, G#m7 ---
        ChordFingering("G#7", "Соль-диез доминантсептаккорд (G#7)", 1,
            listOf(4, 4, 5, 4, 6, 4), barreFret = 4),
        ChordFingering("G#maj7", "Соль-диез большой мажорный (G#maj7)", 1,
            listOf(4, 4, 5, 5, 6, 4), barreFret = 4),
        ChordFingering("G#m7", "Соль-диез минорный септаккорд (G#m7)", 1,
            listOf(4, 4, 4, 4, 6, 4), barreFret = 4),

        // --- A7, Amaj7, Am7 ---
        ChordFingering("A7", "Ля доминантсептаккорд (A7)", 1,
            listOf(0, 2, 0, 2, 0, -1)),
        ChordFingering("Amaj7", "Ля большой мажорный (Amaj7)", 1,
            listOf(0, 2, 1, 2, 0, -1)),
        ChordFingering("Am7", "Ля минорный септаккорд (Am7)", 1,
            listOf(0, 1, 0, 2, 0, -1)),

        // --- A#7, A#maj7, A#m7 ---
        ChordFingering("A#7", "Ля-диез доминантсептаккорд (A#7)", 1,
            listOf(1, 3, 1, 3, 1, -1), barreFret = 1),
        ChordFingering("A#maj7", "Ля-диез большой мажорный (A#maj7)", 1,
            listOf(1, 2, 3, 3, 1, -1), barreFret = 1),
        ChordFingering("A#m7", "Ля-диез минорный септаккорд (A#m7)", 1,
            listOf(1, 2, 1, 3, 1, -1), barreFret = 1),

        // --- B7, Bmaj7, Bm7 ---
        ChordFingering("B7", "Си доминантсептаккорд (B7)", 1,
            listOf(2, 0, 2, 1, 2, -1)),
        ChordFingering("Bmaj7", "Си большой мажорный (Bmaj7)", 1,
            listOf(2, 4, 3, 4, 2, -1), barreFret = 2),
        ChordFingering("Bm7", "Си минорный септаккорд (Bm7)", 1,
            listOf(2, 3, 2, 4, 2, -1), barreFret = 2),

        // ══════════════════════════════════════════════════════════════
        // SUS2 и SUS4
        // ══════════════════════════════════════════════════════════════

        ChordFingering("Csus2", "До sus2 (Csus2)", 1,
            listOf(3, 3, 5, 5, 3, -1), barreFret = 3),
        ChordFingering("Csus4", "До sus4 (Csus4)", 1,
            listOf(1, 1, 0, 3, 3, -1)),
        ChordFingering("Dsus2", "Ре sus2 (Dsus2)", 1,
            listOf(0, 3, 2, 0, -1, -1)),
        ChordFingering("Dsus4", "Ре sus4 (Dsus4)", 1,
            listOf(3, 3, 2, 0, -1, -1)),
        ChordFingering("Esus2", "Ми sus2 (Esus2)", 1,
            listOf(0, 0, 4, 2, 2, 0)),
        ChordFingering("Esus4", "Ми sus4 (Esus4)", 1,
            listOf(0, 0, 2, 2, 2, 0)),
        ChordFingering("Fsus2", "Фа sus2 (Fsus2)", 1,
            listOf(1, 1, 0, 3, 3, 1), barreFret = 1),
        ChordFingering("Fsus4", "Фа sus4 (Fsus4)", 1,
            listOf(1, 1, 3, 3, 3, 1), barreFret = 1),
        ChordFingering("Gsus2", "Соль sus2 (Gsus2)", 1,
            listOf(3, 0, 0, 0, 0, 3)),
        ChordFingering("Gsus4", "Соль sus4 (Gsus4)", 1,
            listOf(3, 1, 0, 0, 3, 3)),
        ChordFingering("Asus2", "Ля sus2 (Asus2)", 1,
            listOf(0, 0, 2, 2, 0, -1)),
        ChordFingering("Asus4", "Ля sus4 (Asus4)", 1,
            listOf(0, 3, 2, 2, 0, -1)),
        ChordFingering("Bsus2", "Си sus2 (Bsus2)", 1,
            listOf(2, 2, 4, 4, 2, -1), barreFret = 2),
        ChordFingering("Bsus4", "Си sus4 (Bsus4)", 1,
            listOf(2, 5, 4, 4, 2, -1), barreFret = 2),

        // ══════════════════════════════════════════════════════════════
        // УМЕНЬШЁННЫЕ (dim) и УВЕЛИЧЕННЫЕ (aug)
        // ══════════════════════════════════════════════════════════════

        ChordFingering("Cdim", "До уменьшённый (Cdim)", 1,
            listOf(-1, 1, 2, 1, 3, -1)),
        ChordFingering("Caug", "До увеличенный (Caug)", 1,
            listOf(0, 1, 1, 2, 3, -1)),
        ChordFingering("Ddim", "Ре уменьшённый (Ddim)", 1,
            listOf(1, 0, 1, 0, -1, -1)),
        ChordFingering("Daug", "Ре увеличенный (Daug)", 1,
            listOf(2, 3, 3, 0, -1, -1)),
        ChordFingering("Edim", "Ми уменьшённый (Edim)", 1,
            listOf(0, -1, 0, 2, 1, 0)),
        ChordFingering("Eaug", "Ми увеличенный (Eaug)", 1,
            listOf(0, 0, 1, 2, 3, 0)),
        ChordFingering("Fdim", "Фа уменьшённый (Fdim)", 1,
            listOf(1, -1, 1, 3, 2, 1), barreFret = 1),
        ChordFingering("Faug", "Фа увеличенный (Faug)", 1,
            listOf(1, 1, 2, 3, -1, -1)),
        ChordFingering("Gdim", "Соль уменьшённый (Gdim)", 1,
            listOf(-1, 0, 3, 2, -1, 3)),
        ChordFingering("Gaug", "Соль увеличенный (Gaug)", 1,
            listOf(3, 0, 1, 0, 2, 3)),
        ChordFingering("Adim", "Ля уменьшённый (Adim)", 1,
            listOf(-1, 1, 2, 1, 0, -1)),
        ChordFingering("Aaug", "Ля увеличенный (Aaug)", 1,
            listOf(1, 2, 2, 2, 0, -1)),
        ChordFingering("Bdim", "Си уменьшённый (Bdim)", 1,
            listOf(-1, 0, 1, 0, 2, -1)),
        ChordFingering("Baug", "Си увеличенный (Baug)", 1,
            listOf(3, 4, 4, 4, 2, -1)),

        // ══════════════════════════════════════════════════════════════
        // СЕКСТАККОРДЫ (6, m6)
        // ══════════════════════════════════════════════════════════════

        ChordFingering("C6", "До секстаккорд (C6)", 1,
            listOf(0, 1, 2, 2, 3, -1)),
        ChordFingering("D6", "Ре секстаккорд (D6)", 1,
            listOf(2, 0, 2, 0, -1, -1)),
        ChordFingering("E6", "Ми секстаккорд (E6)", 1,
            listOf(0, 2, 1, 2, 2, 0)),
        ChordFingering("G6", "Соль секстаккорд (G6)", 1,
            listOf(0, 0, 0, 0, 2, 3)),
        ChordFingering("A6", "Ля секстаккорд (A6)", 1,
            listOf(0, 2, 2, 2, 0, -1)),
        ChordFingering("Am6", "Ля минорный секстаккорд (Am6)", 1,
            listOf(0, 2, 1, 2, 0, -1)),
        ChordFingering("Dm6", "Ре минорный секстаккорд (Dm6)", 1,
            listOf(1, 0, 2, 0, -1, -1)),
        ChordFingering("Em6", "Ми минорный секстаккорд (Em6)", 1,
            listOf(0, 2, 0, 2, 2, 0)),

        // ══════════════════════════════════════════════════════════════
        // УМЕНЬШЁННЫЕ СЕПТАККОРДЫ (dim7) и ПОЛУУМЕНЬШЁННЫЕ (m7b5)
        // ══════════════════════════════════════════════════════════════

        ChordFingering("Cdim7", "До уменьшённый септаккорд (Cdim7)", 1,
            listOf(-1, 1, 2, 1, 3, -1)),
        ChordFingering("Ddim7", "Ре уменьшённый септаккорд (Ddim7)", 1,
            listOf(1, 0, 1, 0, -1, -1)),
        ChordFingering("Edim7", "Ми уменьшённый септаккорд (Edim7)", 1,
            listOf(-1, 2, 0, 1, 1, 0)),
        ChordFingering("Fdim7", "Фа уменьшённый септаккорд (Fdim7)", 1,
            listOf(-1, 0, 1, 0, 1, -1)),
        ChordFingering("Gdim7", "Соль уменьшённый септаккорд (Gdim7)", 1,
            listOf(-1, -1, 2, 3, 2, 3)),
        ChordFingering("Adim7", "Ля уменьшённый септаккорд (Adim7)", 1,
            listOf(-1, 1, 2, 0, 2, -1)),
        ChordFingering("Bdim7", "Си уменьшённый септаккорд (Bdim7)", 1,
            listOf(-1, 0, 1, 2, 0, -1)),

        ChordFingering("Cm7b5", "До полууменьшённый (Cm7b5)", 1,
            listOf(3, 4, 3, 4, 3, -1), barreFret = 3),
        ChordFingering("Dm7b5", "Ре полууменьшённый (Dm7b5)", 1,
            listOf(1, 1, 1, 0, -1, -1)),
        ChordFingering("Em7b5", "Ми полууменьшённый (Em7b5)", 1,
            listOf(0, 0, 0, 0, 1, 0)),
        ChordFingering("Fm7b5", "Фа полууменьшённый (Fm7b5)", 1,
            listOf(1, 1, 1, 1, -1, -1)),
        ChordFingering("Gm7b5", "Соль полууменьшённый (Gm7b5)", 1,
            listOf(-1, -1, 3, 3, 2, 3)),
        ChordFingering("Am7b5", "Ля полууменьшённый (Am7b5)", 1,
            listOf(-1, 1, 0, 1, 0, -1)),
        ChordFingering("Bm7b5", "Си полууменьшённый (Bm7b5)", 1,
            listOf(-1, 0, 1, 2, 0, -1)),

        // ══════════════════════════════════════════════════════════════
        // МИНОРНЫЙ МАЖОРНЫЙ СЕПТАККОРД m(maj7)
        // ══════════════════════════════════════════════════════════════

        ChordFingering("Cm(maj7)", "До минорный мажорный (Cm(maj7))", 1,
            listOf(3, 4, 4, 5, 3, -1), barreFret = 3),
        ChordFingering("Dm(maj7)", "Ре минорный мажорный (Dm(maj7))", 1,
            listOf(1, 2, 2, 0, -1, -1)),
        ChordFingering("Em(maj7)", "Ми минорный мажорный (Em(maj7))", 1,
            listOf(0, 0, 0, 1, 2, 0)),
        ChordFingering("Am(maj7)", "Ля минорный мажорный (Am(maj7))", 1,
            listOf(0, 1, 1, 2, 0, -1)),

        // ══════════════════════════════════════════════════════════════
        // УВЕЛИЧЕННЫЙ СЕПТАККОРД (aug7)
        // ══════════════════════════════════════════════════════════════

        ChordFingering("Caug7", "До увеличенный септаккорд (Caug7)", 1,
            listOf(0, 1, 1, 0, 3, -1)),
        ChordFingering("Eaug7", "Ми увеличенный септаккорд (Eaug7)", 1,
            listOf(0, 0, 1, 0, 3, 0)),
        ChordFingering("Gaug7", "Соль увеличенный септаккорд (Gaug7)", 1,
            listOf(1, 0, 1, 0, 2, 3)),
        ChordFingering("Aaug7", "Ля увеличенный септаккорд (Aaug7)", 1,
            listOf(1, 2, 0, 2, 0, -1)),

        // ══════════════════════════════════════════════════════════════
        // 7sus4 и 7sus2
        // ══════════════════════════════════════════════════════════════

        ChordFingering("C7sus4", "До 7sus4 (C7sus4)", 1,
            listOf(1, 1, 3, 3, 3, -1)),
        ChordFingering("D7sus4", "Ре 7sus4 (D7sus4)", 1,
            listOf(3, 1, 2, 0, -1, -1)),
        ChordFingering("E7sus4", "Ми 7sus4 (E7sus4)", 1,
            listOf(0, 0, 2, 0, 2, 0)),
        ChordFingering("G7sus4", "Соль 7sus4 (G7sus4)", 1,
            listOf(1, 1, 0, 0, 3, 3)),
        ChordFingering("A7sus4", "Ля 7sus4 (A7sus4)", 1,
            listOf(0, 3, 0, 2, 0, -1)),

        ChordFingering("C7sus2", "До 7sus2 (C7sus2)", 1,
            listOf(3, 3, 3, 5, 3, -1), barreFret = 3),
        ChordFingering("D7sus2", "Ре 7sus2 (D7sus2)", 1,
            listOf(0, 1, 2, 0, -1, -1)),
        ChordFingering("E7sus2", "Ми 7sus2 (E7sus2)", 1,
            listOf(0, 0, 4, 0, 2, 0)),
        ChordFingering("A7sus2", "Ля 7sus2 (A7sus2)", 1,
            listOf(0, 0, 0, 2, 0, -1)),

        // ══════════════════════════════════════════════════════════════
        // ADD9 и MADD9
        // ══════════════════════════════════════════════════════════════

        ChordFingering("Cadd9", "До add9 (Cadd9)", 1,
            listOf(0, 3, 0, 2, 3, -1)),
        ChordFingering("Dadd9", "Ре add9 (Dadd9)", 1,
            listOf(0, 3, 2, 0, -1, -1)),
        ChordFingering("Eadd9", "Ми add9 (Eadd9)", 1,
            listOf(0, 0, 1, 4, 2, 0)),
        ChordFingering("Fadd9", "Фа add9 (Fadd9)", 1,
            listOf(3, 1, 2, 3, -1, -1)),
        ChordFingering("Gadd9", "Соль add9 (Gadd9)", 1,
            listOf(3, 0, 0, 0, 0, 3)),
        ChordFingering("Aadd9", "Ля add9 (Aadd9)", 1,
            listOf(0, 0, 2, 2, 0, -1)),

        ChordFingering("Cmadd9", "До минорный add9 (Cmadd9)", 1,
            listOf(3, 3, 5, 5, 3, -1), barreFret = 3),
        ChordFingering("Dmadd9", "Ре минорный add9 (Dmadd9)", 1,
            listOf(0, 3, 2, 0, -1, -1)),
        ChordFingering("Amadd9", "Ля минорный add9 (Amadd9)", 1,
            listOf(0, 1, 0, 2, 0, -1)),

        // ══════════════════════════════════════════════════════════════
        // НОНАККОРДЫ (9, m9, maj9)
        // ══════════════════════════════════════════════════════════════

        ChordFingering("C9", "До нонаккорд (C9)", 1,
            listOf(0, 3, 3, 2, 3, -1)),
        ChordFingering("D9", "Ре нонаккорд (D9)", 1,
            listOf(0, 1, 2, 0, -1, -1)),
        ChordFingering("E9", "Ми нонаккорд (E9)", 1,
            listOf(0, 2, 1, 0, 2, 0)),
        ChordFingering("G9", "Соль нонаккорд (G9)", 1,
            listOf(1, 0, 0, 2, 0, 3)),
        ChordFingering("A9", "Ля нонаккорд (A9)", 1,
            listOf(0, 2, 0, 0, 0, -1)),

        ChordFingering("Cmaj9", "До большой мажорный нонаккорд (Cmaj9)", 1,
            listOf(0, 3, 0, 0, 3, -1)),
        ChordFingering("Dmaj9", "Ре большой мажорный нонаккорд (Dmaj9)", 1,
            listOf(0, 2, 2, 0, -1, -1)),
        ChordFingering("Gmaj9", "Соль большой мажорный нонаккорд (Gmaj9)", 1,
            listOf(2, 0, 0, 0, 0, 3)),

        ChordFingering("Cm9", "До минорный нонаккорд (Cm9)", 1,
            listOf(3, 4, 3, 3, 3, -1), barreFret = 3),
        ChordFingering("Dm9", "Ре минорный нонаккорд (Dm9)", 1,
            listOf(0, 1, 1, 0, -1, -1)),
        ChordFingering("Em9", "Ми минорный нонаккорд (Em9)", 1,
            listOf(0, 0, 0, 0, 0, 0)),
        ChordFingering("Am9", "Ля минорный нонаккорд (Am9)", 1,
            listOf(0, 1, 0, 0, 0, -1)),

        // ══════════════════════════════════════════════════════════════
        // 6/9 и m6/9
        // ══════════════════════════════════════════════════════════════

        ChordFingering("C6/9", "До секстнонаккорд (C6/9)", 1,
            listOf(0, 3, 2, 2, 3, -1)),
        ChordFingering("D6/9", "Ре секстнонаккорд (D6/9)", 1,
            listOf(0, 0, 2, 0, -1, -1)),
        ChordFingering("G6/9", "Соль секстнонаккорд (G6/9)", 1,
            listOf(0, 0, 0, 0, 0, 3)),
        ChordFingering("A6/9", "Ля секстнонаккорд (A6/9)", 1,
            listOf(0, 0, 2, 2, 0, -1)),

        // ══════════════════════════════════════════════════════════════
        // 7b9, 7#9, 7b5, 7#5
        // ══════════════════════════════════════════════════════════════

        ChordFingering("C7b9", "До 7b9 (C7b9)", 1,
            listOf(-1, 2, 3, 2, 3, -1)),
        ChordFingering("E7b9", "Ми 7b9 (E7b9)", 1,
            listOf(0, 1, 1, 0, 2, 0)),
        ChordFingering("A7b9", "Ля 7b9 (A7b9)", 1,
            listOf(-1, 2, 0, 2, 0, -1)),

        ChordFingering("E7#9", "Ми 7#9 (E7#9)", 1,
            listOf(0, 3, 1, 0, 2, 0)),
        ChordFingering("A7#9", "Ля 7#9 (A7#9)", 1,
            listOf(0, 1, 3, 2, 0, -1)),

        ChordFingering("C7b5", "До 7b5 (C7b5)", 1,
            listOf(-1, 1, 3, 2, 3, -1)),
        ChordFingering("E7b5", "Ми 7b5 (E7b5)", 1,
            listOf(0, -1, 1, 0, 1, 0)),

        ChordFingering("C7#5", "До 7#5 (C7#5)", 1,
            listOf(0, 1, 1, 2, 3, -1)),
        ChordFingering("E7#5", "Ми 7#5 (E7#5)", 1,
            listOf(0, 0, 1, 0, 3, 0)),

        // ══════════════════════════════════════════════════════════════
        // POWER CHORDS (5)
        // ══════════════════════════════════════════════════════════════

        ChordFingering("C5", "До квинтаккорд (C5)", 1,
            listOf(-1, -1, -1, 5, 3, -1)),
        ChordFingering("D5", "Ре квинтаккорд (D5)", 1,
            listOf(-1, -1, -1, 7, 5, -1)),
        ChordFingering("E5", "Ми квинтаккорд (E5)", 1,
            listOf(-1, -1, -1, 2, 2, 0)),
        ChordFingering("F5", "Фа квинтаккорд (F5)", 1,
            listOf(-1, -1, -1, 3, 3, 1)),
        ChordFingering("G5", "Соль квинтаккорд (G5)", 1,
            listOf(-1, -1, -1, 5, 5, 3)),
        ChordFingering("A5", "Ля квинтаккорд (A5)", 1,
            listOf(-1, -1, -1, 2, 0, -1)),
        ChordFingering("B5", "Си квинтаккорд (B5)", 1,
            listOf(-1, -1, -1, 4, 2, -1))
    )

    /** Порядок тональностей (хроматическая гамма) */
    val KEY_ORDER = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Русские названия тональностей */
    val KEY_NAMES_RU = mapOf(
        "C" to "До", "C#" to "До-диез",
        "D" to "Ре", "D#" to "Ре-диез",
        "E" to "Ми", "F" to "Фа",
        "F#" to "Фа-диез", "G" to "Соль",
        "G#" to "Соль-диез", "A" to "Ля",
        "A#" to "Ля-диез", "B" to "Си"
    )

    /** Получить аккорд по ID (первая позиция по умолчанию) */
    fun getChord(id: String): ChordFingering? =
        chordDatabase.firstOrNull { it.name == id && it.position == 1 }

    /** Получить аккорд в конкретной позиции */
    fun getChord(id: String, position: Int): ChordFingering? =
        chordDatabase.firstOrNull { it.name == id && it.position == position }

    /** Все аккорды */
    fun getAllChords(): List<ChordFingering> = chordDatabase

    /** Все уникальные ID аккордов */
    fun getChordIds(): List<String> = chordDatabase.map { it.name }.distinct()

    /** Все суффиксы типов аккордов */
    val CHORD_TYPE_SUFFIXES = listOf(
        "", "m", "7", "maj7", "m7", "dim", "aug",
        "sus2", "sus4", "6", "m6",
        "dim7", "m7b5", "m(maj7)", "aug7",
        "7sus2", "7sus4", "6/9", "m6/9",
        "add9", "madd9", "9", "m9", "maj9",
        "7b9", "7#9", "7b5", "7#5",
        "add11", "11", "m11", "maj11", "7#11",
        "13", "m13", "maj13", "alt", "5"
    )

    /** Аккорды для данной тональности (все типы, все позиции) */
    fun getChordsForKey(key: String): List<ChordFingering> =
        chordDatabase.filter { chord ->
            CHORD_TYPE_SUFFIXES.any { suffix -> chord.name == "$key$suffix" }
        }

    /** Аккорды сгруппированные по тональности */
    fun getChordsByKey(): Map<String, List<ChordFingering>> {
        val result = linkedMapOf<String, List<ChordFingering>>()
        for (key in KEY_ORDER) {
            result[key] = getChordsForKey(key)
        }
        return result
    }

    /** Получить группы аккордов для тональности (для UI) */
    fun getChordGroupsForKey(key: String): Map<String, List<ChordFingering>> {
        val all = getChordsForKey(key)
        val groups = linkedMapOf<String, List<ChordFingering>>()

        val majorChords = all.filter { it.name == key }
        if (majorChords.isNotEmpty()) groups["Мажор ($key)"] = majorChords

        val minorChords = all.filter { it.name == "${key}m" }
        if (minorChords.isNotEmpty()) groups["Минор (${key}m)"] = minorChords

        val dom7 = all.filter { it.name == "${key}7" }
        if (dom7.isNotEmpty()) groups["Септаккорд (${key}7)"] = dom7

        val maj7 = all.filter { it.name == "${key}maj7" }
        if (maj7.isNotEmpty()) groups["Мажорный септ. (${key}maj7)"] = maj7

        val m7 = all.filter { it.name == "${key}m7" }
        if (m7.isNotEmpty()) groups["Минорный септ. (${key}m7)"] = m7

        val sus2 = all.filter { it.name == "${key}sus2" }
        if (sus2.isNotEmpty()) groups["Sus2 (${key}sus2)"] = sus2

        val sus4 = all.filter { it.name == "${key}sus4" }
        if (sus4.isNotEmpty()) groups["Sus4 (${key}sus4)"] = sus4

        val dim = all.filter { it.name == "${key}dim" }
        if (dim.isNotEmpty()) groups["Уменьшённый (${key}dim)"] = dim

        val aug = all.filter { it.name == "${key}aug" }
        if (aug.isNotEmpty()) groups["Увеличенный (${key}aug)"] = aug

        val sixth = all.filter { it.name == "${key}6" }
        if (sixth.isNotEmpty()) groups["Секстаккорд (${key}6)"] = sixth

        val m6 = all.filter { it.name == "${key}m6" }
        if (m6.isNotEmpty()) groups["Минорный секст. (${key}m6)"] = m6

        val dim7 = all.filter { it.name == "${key}dim7" }
        if (dim7.isNotEmpty()) groups["Ум. септаккорд (${key}dim7)"] = dim7

        val m7b5 = all.filter { it.name == "${key}m7b5" }
        if (m7b5.isNotEmpty()) groups["Полуум. (${key}m7b5)"] = m7b5

        val mmaj7 = all.filter { it.name == "${key}m(maj7)" }
        if (mmaj7.isNotEmpty()) groups["mMaj7 (${key}m(maj7))"] = mmaj7

        val aug7 = all.filter { it.name == "${key}aug7" }
        if (aug7.isNotEmpty()) groups["Ув. септ. (${key}aug7)"] = aug7

        val sus27 = all.filter { it.name == "${key}7sus2" }
        if (sus27.isNotEmpty()) groups["7sus2 (${key}7sus2)"] = sus27

        val sus47 = all.filter { it.name == "${key}7sus4" }
        if (sus47.isNotEmpty()) groups["7sus4 (${key}7sus4)"] = sus47

        val add9 = all.filter { it.name == "${key}add9" }
        if (add9.isNotEmpty()) groups["Add9 (${key}add9)"] = add9

        val madd9 = all.filter { it.name == "${key}madd9" }
        if (madd9.isNotEmpty()) groups["mAdd9 (${key}madd9)"] = madd9

        val dom9 = all.filter { it.name == "${key}9" }
        if (dom9.isNotEmpty()) groups["9 (${key}9)"] = dom9

        val m9 = all.filter { it.name == "${key}m9" }
        if (m9.isNotEmpty()) groups["m9 (${key}m9)"] = m9

        val maj9 = all.filter { it.name == "${key}maj9" }
        if (maj9.isNotEmpty()) groups["Maj9 (${key}maj9)"] = maj9

        val sixNine = all.filter { it.name == "${key}6/9" }
        if (sixNine.isNotEmpty()) groups["6/9 (${key}6/9)"] = sixNine

        val power = all.filter { it.name == "${key}5" }
        if (power.isNotEmpty()) groups["Power (${key}5)"] = power

        // Remaining chord types
        val remaining = all.filter { chord ->
            groups.values.none { group -> group.contains(chord) }
        }
        if (remaining.isNotEmpty()) groups["Другие ($key)"] = remaining

        return groups
    }
}
