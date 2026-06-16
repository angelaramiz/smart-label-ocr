package com.example.ui

object BarcodeEncoder {

    private val CODE128_PATTERNS = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131"
    )

    private val START_B_PATTERN = "211214"
    private val STOP_PATTERN = "2331112"

    private val EAN_L_CODES = arrayOf(
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011"
    )
    private val EAN_G_CODES = arrayOf(
        "0100111", "0110011", "0011011", "0100001", "0011101",
        "0111001", "0000101", "0010001", "0001001", "0010111"
    )
    private val EAN_R_CODES = arrayOf(
        "1110010", "1100110", "1101100", "1000010", "1011100",
        "1001110", "1010000", "1000100", "1001000", "1110100"
    )
    private val EAN_PARITY = arrayOf(
        arrayOf(0, 0, 0, 0, 0, 0), // 0: LLLLLL
        arrayOf(0, 0, 1, 0, 1, 1), // 1: LLGLGG
        arrayOf(0, 0, 1, 1, 0, 1), // 2: LLGGLG
        arrayOf(0, 0, 1, 1, 1, 0), // 3: LLGGGL
        arrayOf(0, 1, 0, 0, 1, 1), // 4: LGLLGG
        arrayOf(0, 1, 1, 0, 0, 1), // 5: LGGLLG
        arrayOf(0, 1, 1, 1, 0, 0), // 6: LGGGLL
        arrayOf(0, 1, 0, 1, 0, 1), // 7: LGLGLG
        arrayOf(0, 1, 0, 1, 1, 0), // 8: LGLGGL
        arrayOf(0, 1, 1, 0, 1, 0)  // 9: LGGLGL
    )

    /**
     * Encodes text into Code 128 (Subset B) pattern.
     * Returns list of segments, each containing (width in modules, isBar).
     */
    fun encodeCode128(text: String): List<Pair<Int, Boolean>> {
        val cleanText = text.filter { it.code in 32..126 }
        val values = mutableListOf<Int>()
        
        // Start with Set B
        values.add(104)
        
        var checksum = 104
        for (i in cleanText.indices) {
            val charVal = cleanText[i].code - 32
            values.add(charVal)
            checksum += charVal * (i + 1)
        }
        
        val checkDigit = checksum % 103
        values.add(checkDigit)
        
        // Stop pattern
        val patterns = mutableListOf<String>()
        for (v in values) {
            if (v == 104) {
                patterns.add(START_B_PATTERN)
            } else if (v in 0..102) {
                patterns.add(CODE128_PATTERNS[v])
            }
        }
        patterns.add(STOP_PATTERN)
        
        // Convert to widths and bar/space toggles
        val result = mutableListOf<Pair<Int, Boolean>>()
        for (pattern in patterns) {
            var isBar = true
            for (char in pattern) {
                val width = char.toString().toInt()
                result.add(Pair(width, isBar))
                isBar = !isBar
            }
        }
        return result
    }

    /**
     * Encodes a UPC to EAN-13 binary string.
     * Pad to 12 digits, computes checksum for 13th digit, and returns a 95-module string of '1's and '0's.
     */
    fun encodeEan13(upc: String): String {
        // Clean UPC digits
        val digitsOnly = upc.filter { it.isDigit() }
        
        // We need 12 digits to calculate the 13th.
        val base12 = when {
            digitsOnly.length >= 12 -> digitsOnly.substring(0, 12)
            else -> digitsOnly.padStart(12, '0')
        }
        
        // Calculate EAN-13 checksum
        var sum = 0
        for (i in 0 until 12) {
            val digit = base12[i].toString().toInt()
            sum += if (i % 2 == 1) digit * 3 else digit * 1
        }
        val checkDigit = (10 - (sum % 10)) % 10
        val full13 = base12 + checkDigit
        
        val systemDigit = full13[0].toString().toInt()
        val builder = java.lang.StringBuilder()
        
        // Start Guard
        builder.append("101")
        
        // Left 6 digits
        for (i in 1..6) {
            val digit = full13[i].toString().toInt()
            val parity = EAN_PARITY[systemDigit][i - 1]
            if (parity == 0) {
                builder.append(EAN_L_CODES[digit])
            } else {
                builder.append(EAN_G_CODES[digit])
            }
        }
        
        // Center Guard
        builder.append("01010")
        
        // Right 6 digits (including checksum digit)
        for (i in 7..12) {
            val digit = full13[i].toString().toInt()
            builder.append(EAN_R_CODES[digit])
        }
        
        // End Guard
        builder.append("101")
        
        return builder.toString()
    }
}
