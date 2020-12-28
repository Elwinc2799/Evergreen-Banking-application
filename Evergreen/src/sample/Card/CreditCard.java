package sample.Card;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import sample.ReadFile;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;

public class CreditCard extends Card {

    public int overDraftCounter;
    public int latePaymentCounter;

    public double outstandingBalance;
    public double expenditure;
    public double minimumPayment = getOutstandingBalance() * 0.05;
    public double annualRates = 1.2;
    public double[] balanceRecorder;
    public double[][] additionalRates = { { 12, 13, 15 },
                                            { 5, 7, 10 }
    };

    public LocalDate cardLastPaidDate;
    public boolean outstandingBalanceStatusUpdated;

    public int getLatePaymentCounter() { return latePaymentCounter; }

    public void setLatePaymentCounter(int latePaymentCounter) { this.latePaymentCounter = latePaymentCounter; }

    public int getOverDraftCounter() { return overDraftCounter; }

    public void setOverDraftCounter(int exceedLimitCounter) { this.overDraftCounter = exceedLimitCounter; }

    public double getOutstandingBalance() { return outstandingBalance; }

    public void setOutstandingBalance(double outstandingBalance) { this.outstandingBalance = outstandingBalance; }

    public double getExpenditure() { return expenditure; }

    public void setExpenditure(double expenditure) { this.expenditure = expenditure; }

    public double getMinimumPayment() { return minimumPayment; }

    public double getAnnualRates() { return annualRates; }

    public LocalDate getCardLastPaidDate() { return cardLastPaidDate; }

    public void setCardLastPaidDate(LocalDate cardLastPaidDate) { this.cardLastPaidDate = cardLastPaidDate; }

    public boolean isOutstandingBalanceStatusUpdated() { return outstandingBalanceStatusUpdated; }

    public void setOutstandingBalanceStatusUpdated(boolean outstandingBalanceStatusUpdated) { this.outstandingBalanceStatusUpdated = outstandingBalanceStatusUpdated; }

    public double getBalanceRecorder(int index) { return balanceRecorder[index]; }

    public void setBalanceRecorder(double[] balanceRecorder) { this.balanceRecorder = balanceRecorder; }

    public void updateOutstandingBalance() {
        updateLatePaymentCounter();
        int lateCategory = 0;
        int overdraftCategory;
        int counter = getLatePaymentCounter();
        LocalDate dateBefore = getCardLastPaidDate().plusMonths(1);
        LocalDate dateAfter = LocalDate.now();

        if (!dateBefore.getMonth().equals(dateAfter.getMonth()) && !isOutstandingBalanceStatusUpdated()) {
            if (counter >= 4 && counter < 8)
                lateCategory = 1;
            else if (counter >= 8 && counter < 12)
                lateCategory = 2;

            if (getOutstandingBalance() - getFixedMonthlyLimit() > 50000)
                overdraftCategory = 2;
            else if (getOutstandingBalance() - getFixedMonthlyLimit() >= 25000 && getOutstandingBalance() - getFixedMonthlyLimit() < 50000)
                overdraftCategory = 1;
            else
                overdraftCategory = 0;

            outstandingBalance += outstandingBalance * (getAnnualRates() / 1200);

            int day = (int) ChronoUnit.DAYS.between(dateBefore, dateAfter);

            if (day > 0) {
                outstandingBalance += (counter > 0) ? (outstandingBalance * (day * (additionalRates[0][lateCategory] / 36500))) : 0;
                outstandingBalance += (getOutstandingBalance() > 0) ? (outstandingBalance * (day * (additionalRates[1][overdraftCategory] / 36500))) : 0;
            }

            outstandingBalance = (double) Math.round(outstandingBalance * 100) / 100;
            setOutstandingBalanceStatusUpdated(true);
            new Thread(updateStatus).start();

            try {
                Class.forName("oracle.jdbc.OracleDriver");
                Statement statement = ReadFile.connect.createStatement();

                statement.executeQuery("UPDATE CREDITCARD SET CARD_OUTSTANDING_BALANCE = " + getOutstandingBalance() +
                        " WHERE USERNAME = '" + ReadFile.DataStorage.getUsername() + "'");
            } catch (SQLException | ClassNotFoundException e) { e.printStackTrace(); }
        }
    }

    Task<Void> updateStatus = new Task<>() {
        @Override
        protected Void call() {
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                Statement statement = ReadFile.connect.createStatement();

                statement.executeQuery("UPDATE CREDITCARD SET CARD_BALANCE_PAID = 'Y'" +
                        " WHERE USERNAME = '" + ReadFile.DataStorage.getUsername() + "'");
            } catch (SQLException | ClassNotFoundException e) { e.printStackTrace(); }

            return null;
        }
    };

    public boolean creditRepayment(double amount) {
        if (amount < getMinimumPayment()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setContentText("Amount is less than the minimum payment (5%).");
            alert.showAndWait();
            return false;
        }

        LocalDate newPaidMonth = LocalDate.now();
        outstandingBalance -= amount;
        setCardLastPaidDate(newPaidMonth);
        new Thread(updateDateTask).start();

        return true;
    }

    Task<Void> updateDateTask = new Task<>() {
        @Override
        protected Void call() {
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                PreparedStatement preparedStatement = ReadFile.connect.prepareStatement
                        ("UPDATE CREDITCARD SET CARD_LAST_REPAYMENT_DATE = ? WHERE USERNAME = ?" );
                preparedStatement.setDate(1, java.sql.Date.valueOf(LocalDate.now()));
                preparedStatement.setString(2, ReadFile.DataStorage.getUsername());
                preparedStatement.execute();

            } catch (SQLException | ClassNotFoundException e) { e.printStackTrace(); }

            return null;
        }
    };

    Task<Void> updateExpenditureTask = new Task<>() {
        @Override
        protected Void call() {
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                Statement statement = ReadFile.connect.createStatement();

                statement.executeQuery("UPDATE CREDITCARD SET CARD_EXPENDITURE = " + 0 +
                        " WHERE USERNAME = '" + ReadFile.DataStorage.getUsername() + "'");

            } catch (SQLException | ClassNotFoundException e) { e.printStackTrace(); }

            return null;
        }
    };

    public void updateLatePaymentCounter() {
        LocalDate dateBefore = getCardLastPaidDate().plusMonths(1);
        LocalDate dateAfter = LocalDate.now();

        setLatePaymentCounter((int) ChronoUnit.MONTHS.between(dateBefore, dateAfter));
    }

    public void creditUsage(double amount) {
        outstandingBalance += amount;
        expenditure += amount;

        setOverDraftCounter(getOverDraftCounter() + ((getExpenditure() > getFixedMonthlyLimit()) ? 1 : 0));
    }

    public boolean creditCardUsageValidation(double amount) {
        if (expenditure + amount > getFixedMonthlyLimit()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setContentText("You have exceed your monthly transaction limit");
            alert.showAndWait();
            return false;
        }

        return true;
    }
}