package com.eventflow.budget.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.exception.BudgetExceededException;

/** Source terms and a confirmed payment are different business facts. */
public final class SourceExpenseRules {
    private SourceExpenseRules() {}
    public static String fingerprint(String description,BigDecimal amount,LocalDate date,String status) {
        String data=Base64.getEncoder().encodeToString(description.strip().getBytes(StandardCharsets.UTF_8))+"|"
                +amount.stripTrailingZeros().toPlainString()+"|"+date+"|"+status;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static boolean cancelled(String status) { return "ANNULE".equals(status) || "ANNULEE".equals(status); }
    public static boolean paid(Depense expense) { return expense!=null && "PAYEE".equals(expense.getStatut()); }
    public static BigDecimal amount(Depense expense,BigDecimal proposed,boolean cancelled) {
        if (paid(expense)) {
            if (!cancelled && expense.getMontant().compareTo(proposed)!=0)
                throw new BudgetExceededException("Cette dépense est déjà payée. Son montant ne peut pas être modifié par une réservation ou un contrat. Aucun remboursement n’est supposé.");
            return expense.getMontant();
        }
        return proposed;
    }
    public static String status(Depense expense,boolean cancelled) { return paid(expense) ? "PAYEE" : cancelled ? "ANNULEE" : "EN_ATTENTE"; }
    public static boolean matches(Depense expense,String description,BigDecimal amount,LocalDate date,String status) {
        if (expense.getSourceFingerprint()!=null) return expense.getSourceFingerprint().equals(fingerprint(description,amount,date,status));
        return expense.getDescription().equals(description) && expense.getMontant().compareTo(amount)==0
                && expense.getDateDepense().equals(date)
                && (expense.isSourceCancelled() || "ANNULEE".equals(expense.getStatut()))==cancelled(status);
    }
    public static void apply(Depense expense,String description,BigDecimal amount,String category,LocalDate date,String sourceStatus) {
        boolean cancelled=cancelled(sourceStatus);
        var financialAmount=amount(expense,amount,cancelled);
        String financialStatus=status(expense,cancelled);
        // Paid dates, labels and amounts are kept as recorded, not rewritten by upstream metadata.
        if (!paid(expense)) expense.update(description,financialAmount,category,date,financialStatus,expense.getCommentaire());
        expense.updateSource(cancelled,fingerprint(description,amount,date,sourceStatus));
    }
}
