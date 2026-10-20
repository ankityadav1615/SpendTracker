package com.spendtracker.parser

import com.spendtracker.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CategorizerTest {

    private fun categoryOf(body: String, sender: String = "VM-HDFCBK-S"): Category {
        val parsed = SmsParser.parse(sender, body)
        assertNotNull("Expected a transaction for: $body", parsed)
        return Categorizer.categorize(parsed!!, body)
    }

    @Test
    fun foodDelivery() {
        assertEquals(
            Category.FOOD,
            categoryOf("Sent Rs.500.00 From HDFC Bank A/C *1234 To SWIGGY On 20/10/24 Ref 434212345678"),
        )
        assertEquals(
            Category.FOOD,
            categoryOf("INR 320.00 debited\nA/c no. XX4321\n20-10-24, 18:22:11\nUPI/P2M/429812345678/ZOMATO LTD\nAxis Bank"),
        )
        assertEquals(
            Category.FOOD,
            categoryOf("Rs.350.00 debited from A/c XX1234 on 20-10-24 Info: UPI-SWIGGY-swiggy@icici-ICIC0DC0099-429512345678-Payment."),
        )
    }

    @Test
    fun groceriesByNameAndByVpa() {
        assertEquals(
            Category.GROCERIES,
            categoryOf("ICICI Bank Acct XX123 debited for Rs 1,250.00 on 21-Oct-24; DMART AVENUE credited. UPI:429612345678."),
        )
        assertEquals(
            Category.GROCERIES,
            categoryOf("Rs.845.00 debited from a/c **1234 on 13-10-24 to VPA blinkit.payu@hdfcbank (UPI Ref No. 428712345678)."),
        )
    }

    @Test
    fun fuel() {
        assertEquals(
            Category.FUEL,
            categoryOf("Rs.2,000.00 spent on your SBI Credit Card ending 1234 at INDIAN OIL PETROL PUMP on 12/10/24."),
        )
    }

    @Test
    fun emiFromLenderNameAndNach() {
        assertEquals(
            Category.EMI,
            categoryOf(
                "Your A/c XX1234 has been debited with INR 12,345.00 on 05-10-2024 towards NACH-DR-BAJAJ FINANCE LTD. " +
                    "Avl Bal INR 45,678.90",
            ),
        )
        assertEquals(
            Category.EMI,
            categoryOf("Rs 8,000.00 debited from A/c XX1234 on 05-10-24 for ACH-DR XYZ HOMES. Avl Bal Rs 1,000.00"),
        )
    }

    @Test
    fun rentFromSmsText() {
        assertEquals(
            Category.RENT,
            categoryOf("Rs 15,000.00 sent from A/c XX1234 to MR SHARMA on 01-10-24 for house rent. UPI Ref 427412345670"),
        )
    }

    @Test
    fun shoppingEntertainmentTravel() {
        assertEquals(
            Category.SHOPPING,
            categoryOf("Thank you for using your HDFC Bank Credit Card ending 4321 for Rs 2,499.00 at AMAZON PAY INDIA on 2024-10-22"),
        )
        assertEquals(
            Category.ENTERTAINMENT,
            categoryOf("Sent Rs.199.00 from Kotak Bank AC X5678 to netflix@hdfcbank on 22-10-24.UPI Ref 429912345678."),
        )
        assertEquals(
            Category.TRAVEL,
            categoryOf("₹250.00 paid to Uber India from your IDFC FIRST Bank a/c XX1234 via UPI. Ref 429612340000"),
        )
    }

    @Test
    fun atmIsCash() {
        assertEquals(Category.CASH, categoryOf("Rs.5000.00 withdrawn from A/c XX1234 on 06-10-24 at ATM S1ANBG01. Avl Bal Rs.40678.90"))
    }

    @Test
    fun creditCardBillPaymentIsExcluded() {
        assertEquals(
            Category.CARD_BILL,
            categoryOf("Rs 15,000.00 debited from A/c XX1234 on 10-10-24 towards payment of your Credit Card XX5678."),
        )
    }

    @Test
    fun personTransfers() {
        assertEquals(
            Category.TRANSFERS,
            categoryOf("Rs 18000.00 debited from A/c XX1234 for IMPS to RAJESH GUPTA on 01-10-24. IMPS Ref no 427412345678"),
        )
        assertEquals(
            Category.DAILY,
            categoryOf("Dear UPI user A/C X1234 debited by 250.0 on date 20Oct24 trf to RAMESH KUMAR Refno 429312345678. -SBI"),
        )
    }

    @Test
    fun salaryByKeyword() {
        assertEquals(
            Category.SALARY,
            categoryOf("Dear Customer, Your A/c XXXXXXXX1234 is credited by Rs.75,000.00 on 30-09-24 by SALARY SEP24 ACME CORP. -SBI"),
        )
    }

    @Test
    fun salaryByCompanyNeftCredit() {
        assertEquals(
            Category.SALARY,
            categoryOf(
                "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-24 for NEFT Cr-CITI0000001-" +
                    "ACME TECHNOLOGIES PVT LTD-RAHUL SHARMA-CITIN24123456789.Avl bal INR 1,02,345.67.",
            ),
        )
    }

    @Test
    fun otherCredits() {
        assertEquals(
            Category.OTHER_INCOME,
            categoryOf("Money Received - INR 1,500.00 in your HDFC Bank A/c xx1234 on 21-10-24 by VPA rahul.sharma@okaxis (UPI Ref No 429512345678)"),
        )
        assertEquals(
            Category.REFUND,
            categoryOf("Rs 500.00 credited to your A/c XX1234 on 21-10-24 as reversal of failed UPI transaction 429512345678"),
        )
    }
}
