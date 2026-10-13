// Copyright (c) 2006 Damien Miller <djm@mindrot.org>
//
// Permission to use, copy, modify, and distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
// ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
// OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
//
// jBCrypt 0.4 를 Kotlin Multiplatform(commonMain) 으로 옮긴 파일입니다. 과제 구현 시 수정할 필요가 없습니다.
// - $2b$ / $2y$ 해시도 검증할 수 있게 확장 (web 의 bcryptjs 는 $2b$ 를 만든다)
// - char64 범위 검사 off-by-one 수정

package com.wisebirds.assignment.utils

import dev.whyoleg.cryptography.random.CryptographyRandom

/** OpenBSD bcrypt 비밀번호 해시. web 의 bcryptjs 와 같은 형식을 만들고 검증합니다. */
internal object BCrypt {
    private const val DEFAULT_LOG_ROUNDS = 10
    private const val SALT_LENGTH = 16
    private const val BLOWFISH_ROUNDS = 16

    // bcrypt IV: "OrpheanBeholderScryDoubt"
    private val CIPHER_TEXT = intArrayOf(0x4f727068, 0x65616e42, 0x65686f6c, 0x64657253, 0x63727944, 0x6f756274)

    // bcrypt 전용 base64 문자표 (MIME base64 와 순서가 다름)
    private const val BASE64_CODE = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val BASE64_INDEX = IntArray(128) { -1 }.also { table ->
        BASE64_CODE.forEachIndexed { index, character -> table[character.code] = index }
    }

    fun generateSalt(logRounds: Int = DEFAULT_LOG_ROUNDS): String {
        require(logRounds in 4..30) { "logRounds must be between 4 and 30" }
        val randomBytes = CryptographyRandom.nextBytes(SALT_LENGTH)
        val builder = StringBuilder("\$2a\$")
        if (logRounds < 10) {
            builder.append('0')
        }
        builder.append(logRounds).append('$').append(encodeBase64(randomBytes, randomBytes.size))
        return builder.toString()
    }

    fun hashPassword(password: String, salt: String): String {
        require(salt.length >= 4 && salt[0] == '$' && salt[1] == '2') { "Invalid salt version" }
        val minor: Char?
        val offset: Int
        if (salt[2] == '$') {
            minor = null
            offset = 3
        } else {
            minor = salt[2]
            require(minor in "aby" && salt[3] == '$') { "Invalid salt revision" }
            offset = 4
        }
        require(salt.length >= offset + 25) { "Invalid salt length" }
        require(salt[offset + 2] == '$') { "Missing salt rounds" }
        val logRounds = salt.substring(offset, offset + 2).toInt()
        val saltBytes = decodeBase64(salt.substring(offset + 3, offset + 25), SALT_LENGTH)
        val passwordBytes = (if (minor != null) password + "\u0000" else password).encodeToByteArray()
        val hashed = Blowfish().cryptRaw(passwordBytes, saltBytes, logRounds)

        val builder = StringBuilder("\$2")
        if (minor != null) {
            builder.append(minor)
        }
        builder.append('$')
        if (logRounds < 10) {
            builder.append('0')
        }
        builder.append(logRounds).append('$')
            .append(encodeBase64(saltBytes, saltBytes.size))
            .append(encodeBase64(hashed, CIPHER_TEXT.size * 4 - 1))
        return builder.toString()
    }

    fun checkPassword(plaintext: String, hashed: String): Boolean {
        val candidate = runCatching { hashPassword(plaintext, hashed) }.getOrNull() ?: return false
        if (candidate.length != hashed.length) {
            return false
        }
        var difference = 0
        for (index in candidate.indices) {
            difference = difference or (candidate[index].code xor hashed[index].code)
        }
        return difference == 0
    }

    private fun encodeBase64(data: ByteArray, length: Int): String {
        require(length in 1..data.size) { "Invalid length" }
        val builder = StringBuilder()
        var offset = 0
        while (offset < length) {
            var first = data[offset++].toInt() and 0xff
            builder.append(BASE64_CODE[(first shr 2) and 0x3f])
            first = (first and 0x03) shl 4
            if (offset >= length) {
                builder.append(BASE64_CODE[first and 0x3f])
                break
            }
            var second = data[offset++].toInt() and 0xff
            first = first or ((second shr 4) and 0x0f)
            builder.append(BASE64_CODE[first and 0x3f])
            first = (second and 0x0f) shl 2
            if (offset >= length) {
                builder.append(BASE64_CODE[first and 0x3f])
                break
            }
            second = data[offset++].toInt() and 0xff
            first = first or ((second shr 6) and 0x03)
            builder.append(BASE64_CODE[first and 0x3f])
            builder.append(BASE64_CODE[second and 0x3f])
        }
        return builder.toString()
    }

    private fun char64(character: Char): Int =
        if (character.code >= BASE64_INDEX.size) -1 else BASE64_INDEX[character.code]

    private fun decodeBase64(text: String, maximumLength: Int): ByteArray {
        val output = ByteArray(maximumLength)
        var offset = 0
        var outputLength = 0
        while (offset < text.length - 1 && outputLength < maximumLength) {
            val first = char64(text[offset++])
            val second = char64(text[offset++])
            if (first == -1 || second == -1) {
                break
            }
            output[outputLength] = ((first shl 2) or ((second and 0x30) shr 4)).toByte()
            if (++outputLength >= maximumLength || offset >= text.length) {
                break
            }
            val third = char64(text[offset++])
            if (third == -1) {
                break
            }
            output[outputLength] = (((second and 0x0f) shl 4) or ((third and 0x3c) shr 2)).toByte()
            if (++outputLength >= maximumLength || offset >= text.length) {
                break
            }
            val fourth = char64(text[offset++])
            output[outputLength] = (((third and 0x03) shl 6) or fourth).toByte()
            ++outputLength
        }
        return output.copyOf(outputLength)
    }

    /** 해시 1회분의 Blowfish 키 스케줄 상태 */
    private class Blowfish {
        private val p = BlowfishTables.P.copyOf()
        private val s = BlowfishTables.S.copyOf()

        fun cryptRaw(password: ByteArray, salt: ByteArray, logRounds: Int): ByteArray {
            require(logRounds in 4..30) { "Bad number of rounds" }
            require(salt.size == SALT_LENGTH) { "Bad salt length" }
            expensiveKeySchedule(salt, password)
            repeat(1 shl logRounds) {
                key(password)
                key(salt)
            }
            val cipherText = CIPHER_TEXT.copyOf()
            repeat(64) {
                for (block in 0 until cipherText.size / 2) {
                    encipher(cipherText, block shl 1)
                }
            }
            val result = ByteArray(cipherText.size * 4)
            cipherText.forEachIndexed { index, word ->
                result[index * 4] = (word ushr 24).toByte()
                result[index * 4 + 1] = (word ushr 16).toByte()
                result[index * 4 + 2] = (word ushr 8).toByte()
                result[index * 4 + 3] = word.toByte()
            }
            return result
        }

        private fun encipher(block: IntArray, offset: Int) {
            var left = block[offset] xor p[0]
            var right = block[offset + 1]
            var round = 0
            while (round <= BLOWFISH_ROUNDS - 2) {
                // Feistel substitution on left word
                var mixed = s[(left shr 24) and 0xff]
                mixed += s[0x100 or ((left shr 16) and 0xff)]
                mixed = mixed xor s[0x200 or ((left shr 8) and 0xff)]
                mixed += s[0x300 or (left and 0xff)]
                right = right xor (mixed xor p[++round])

                // Feistel substitution on right word
                mixed = s[(right shr 24) and 0xff]
                mixed += s[0x100 or ((right shr 16) and 0xff)]
                mixed = mixed xor s[0x200 or ((right shr 8) and 0xff)]
                mixed += s[0x300 or (right and 0xff)]
                left = left xor (mixed xor p[++round])
            }
            block[offset] = right xor p[BLOWFISH_ROUNDS + 1]
            block[offset + 1] = left
        }

        // data 를 순환하며 4바이트씩 읽어 32비트 word 로 만든다. offsetHolder[0] 이 다음 읽을 위치
        private fun streamToWord(data: ByteArray, offsetHolder: IntArray): Int {
            var word = 0
            var offset = offsetHolder[0]
            repeat(4) {
                word = (word shl 8) or (data[offset].toInt() and 0xff)
                offset = (offset + 1) % data.size
            }
            offsetHolder[0] = offset
            return word
        }

        private fun key(key: ByteArray) {
            val keyOffset = intArrayOf(0)
            val block = intArrayOf(0, 0)
            for (index in p.indices) {
                p[index] = p[index] xor streamToWord(key, keyOffset)
            }
            for (index in p.indices step 2) {
                encipher(block, 0)
                p[index] = block[0]
                p[index + 1] = block[1]
            }
            for (index in s.indices step 2) {
                encipher(block, 0)
                s[index] = block[0]
                s[index + 1] = block[1]
            }
        }

        // Provos & Mazieres 의 "enhanced key schedule"
        private fun expensiveKeySchedule(data: ByteArray, key: ByteArray) {
            val keyOffset = intArrayOf(0)
            val dataOffset = intArrayOf(0)
            val block = intArrayOf(0, 0)
            for (index in p.indices) {
                p[index] = p[index] xor streamToWord(key, keyOffset)
            }
            for (index in p.indices step 2) {
                block[0] = block[0] xor streamToWord(data, dataOffset)
                block[1] = block[1] xor streamToWord(data, dataOffset)
                encipher(block, 0)
                p[index] = block[0]
                p[index + 1] = block[1]
            }
            for (index in s.indices step 2) {
                block[0] = block[0] xor streamToWord(data, dataOffset)
                block[1] = block[1] xor streamToWord(data, dataOffset)
                encipher(block, 0)
                s[index] = block[0]
                s[index + 1] = block[1]
            }
        }
    }
}
