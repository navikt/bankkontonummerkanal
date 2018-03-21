package no.nav.altinn.validators;

/**
 * Modulus checking of bank account numbers
 */
public class ModulusChecker {
    private static final int[] bankAccountWeightNumbers = new int[] {
            5, 4, 3, 2, 7, 6, 5, 4, 3, 2
    };

    public static boolean validateBankAccountNumber(String bankAccountNumber) {
        int sum = 0;
        String pureNumber = bankAccountNumber.replaceAll("[. ]", "");
        for (int i = 0; i < 10; i++) {
            sum += (pureNumber.charAt(i) - '0') * bankAccountWeightNumbers[i];
        }

        return ((sum) % 11) == (pureNumber.charAt(10)-'0');
    }
}
