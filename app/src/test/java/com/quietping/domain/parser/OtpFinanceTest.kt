package com.quietping.domain.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [PatternCatalog] OTP + finance detection (Bundle 4). Detection must
 * fire on real codes/finance copy but stay quiet on ordinary messages.
 */
class OtpFinanceTest {

    @Test
    fun `recognizes common OTP phrasings`() {
        assertThat(PatternCatalog.isOtp("123456 is your verification code")).isTrue()
        assertThat(PatternCatalog.isOtp("Your OTP is 4821. Do not share it.")).isTrue()
        assertThat(PatternCatalog.isOtp("Use code 90210 to log in")).isTrue()
        assertThat(PatternCatalog.isOtp("G-558212 is your Google verification code")).isTrue()
    }

    @Test
    fun `does not treat ordinary messages as OTP`() {
        assertThat(PatternCatalog.isOtp("Call me at 555 1234 when you land")).isFalse()
        assertThat(PatternCatalog.isOtp("See you at 8 for dinner")).isFalse()
        assertThat(PatternCatalog.isOtp("your code")).isFalse() // wording but no digits
    }

    @Test
    fun `extracts the OTP digits, stripping grouping`() {
        assertThat(PatternCatalog.extractOtp("123456 is your verification code")).isEqualTo("123456")
        assertThat(PatternCatalog.extractOtp("Your code is 123-456")).isEqualTo("123456")
        assertThat(PatternCatalog.extractOtp("dinner at 8")).isNull()
    }

    @Test
    fun `recognizes finance and bill messages`() {
        assertThat(PatternCatalog.isFinance("Your electricity bill of Rs. 1,240 is due on 30th")).isTrue()
        assertThat(PatternCatalog.isFinance("INR 5000 debited from your account")).isTrue()
        assertThat(PatternCatalog.isFinance("Your EMI of \$120 is overdue")).isTrue()
    }

    @Test
    fun `does not treat plain chat as finance`() {
        assertThat(PatternCatalog.isFinance("are we still on for tonight?")).isFalse()
        assertThat(PatternCatalog.isFinance("happy birthday!")).isFalse()
    }
}
