package com.jelurida.ardor.contracts;

import nxt.addons.*;
import nxt.blockchain.TransactionTypeEnum;
import nxt.http.EncryptTo;
import nxt.http.callers.*;
import nxt.http.responses.TransactionResponse;
import nxt.util.Time;
import nxt.crypto.*;
import nxt.messaging.*;
import org.bouncycastle.jcajce.util.MessageDigestUtils;
import nxt.account.Account;
import nxt.util.Convert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static nxt.blockchain.TransactionTypeEnum.*;

public class Gift extends AbstractContract {

    @ContractParametersProvider
    public interface Params {

        @ContractRunnerParameter
        String secretPhrase();
    }

    /**
     * If there is a sendMoney tx to contract account (except on AEUR) then return a token to redeem that deposit
     */
    @Override
    @ValidateTransactionType(accept = { PARENT_PAYMENT, CHILD_PAYMENT }) // These are the transaction types accepted by the contract
    @ValidateContractRunnerIsRecipient() // Validate that the payment was made to the contract runner account
    @ValidateChain(reject = 3) // Do not process payments made on the AEUR chain (just example)
    public JO processTransaction(TransactionContext context) {
        TransactionResponse transaction = context.getTransaction();
        if (transaction.isPhased()) {
            // We cannot allow phased transactions
            // Therefore in this case we just refund the same amount.
            SendMoneyCall sendMoneyCall = SendMoneyCall.create(transaction.getChainId()).
                    recipient(transaction.getSender()).
                    amountNQT(transaction.getAmount());
            return context.createTransaction(sendMoneyCall);
        }

        Params params = context.getParams(Params.class);

        JO token = GenerateTokenCall.create().
                website(transaction.getAmount() + "|" + transaction.getChainId()).
                secretPhrase(params.secretPhrase()).
                call();

        JO tokenView = GenerateTokenCall.create().
                website(transaction.getAmount() + "|" + transaction.getChainId() + "|view").
                secretPhrase(params.secretPhrase()).
                call();
        context.logInfoMessage(String.format("INFO: token: %s |  passphrase: %s", token, params.secretPhrase()));
        // Encrypt the message
        EncryptedData encryptedMsg = context.getConfig().encryptTo(Account.getPublicKey(context.getSenderId()), Convert.toBytes(token.getString("token")+"|||"+tokenView.getString("token"), true), true);


        SendMessageCall returnMsg = SendMessageCall.create(2).
                recipient(transaction.getSender()).
                encryptedMessageData(encryptedMsg.getData()).
                encryptedMessageNonce(encryptedMsg.getNonce()).
                encryptedMessageIsPrunable(true);

        return context.createTransaction(returnMsg);
    }
    /**
     * Look for gift redemptions token and return data if redeemed or try to redeem
     * @param context api request context
     */
    @Override
    public JO processRequest(RequestContext context) {
        String giftAddress = context.getAccountRs();
        String contractParamsStr = context.getParameter("setupParams");
        if (contractParamsStr == null) {
            return context.generateErrorResponse(10001, "Please specify a token as the setupParams");
        }
        JO params;
        try {
            params = JO.parse(contractParamsStr);
        } catch (Exception e) {
            return context.generateErrorResponse(10001, "Invalid JSON sent");
        }

        //context.logInfoMessage(String.format("INFO: params: %s", params));
        String token = params.getString("token");
        if (token == null || token.isEmpty()) {
            return context.generateErrorResponse(10001, "No token sent");
        }
        String tokenView = params.getString("tokenView");
        if (tokenView == null || tokenView.isEmpty()) {
            return context.generateErrorResponse(10001, "No view token sent");
        }
        String recipient = params.getString("recipient");
        if (recipient == null || recipient.isEmpty()) {
            return context.generateErrorResponse(10001, "No recipient sent");
        }
        long giftAmount = params.getLong("giftAmount");
        if (giftAmount <= 0) {
            return context.generateErrorResponse(10001, "No amount sent or invalid amount");
        }
        int giftType = params.getInt("giftType");
        if (giftType < 1 || giftType > 5) {
            return context.generateErrorResponse(10001, "No type sent or invalid type");
        }
        String tokenType = params.getString("tokenType");
        if (tokenType == null || tokenType.isEmpty()) {
            return context.generateErrorResponse(10001, "No token type sent");
        }

        // The view token should always be sent so we can grab and validate that first
        JO validateTokenView = DecodeTokenCall.create().
                website(giftAmount + "|" + giftType + "|view").
                token(tokenView).
                call();

        boolean validViewToken = validateTokenView.getBoolean("valid");
        if (!validViewToken) {
            return context.generateErrorResponse(10001, "Invalid view token");
        }
        String tokenViewAddress = validateTokenView.getString("accountRS");
        if (!tokenViewAddress.equals(giftAddress)) {
            return context.generateErrorResponse(10001, "View token from incorrect account");
        }

        String md5viewToken;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(StandardCharsets.UTF_8.encode(tokenView));
            md5viewToken = String.format("%032x", new BigInteger(1, md5.digest()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        //Check for view token property to see if it is already redeemed
        JO viewProperty = GetAccountPropertiesCall.create().
                setter(context.getAccount()).
                recipient(context.getAccount()).
                property(md5viewToken).
                call();

        // Check if it is the view or redeem token
        if (tokenType.equals("view")){
           if (viewProperty.getArray("properties").size() > 0) { // Token already redeemed
               JO response = new JO();
               response.put("token","redeemed");
               return response;
           } else {
               JO response = new JO();
               response.put("token","valid"); // Token is valid and available to be redeemed still
               return response;
           }

        } else {
            if (viewProperty.getArray("properties").size() > 0) { // Token already redeemed
                return context.generateErrorResponse(10001, "Token is already redeemed");
            } else {
                JO validateToken = DecodeTokenCall.create().
                        website(giftAmount + "|" + giftType).
                        token(token).
                        call();

                boolean validToken = validateToken.getBoolean("valid");
                if (!validToken) {
                    return context.generateErrorResponse(10001, "Invalid token");
                }
                String tokenAddress = validateToken.getString("accountRS");
                if (!tokenAddress.equals(giftAddress)) {
                    return context.generateErrorResponse(10001, "Token from incorrect account");
                }

                SetAccountPropertyCall setAccountToken = SetAccountPropertyCall.create(2).
                        recipient(context.getAccount()).
                        property(md5viewToken).
                        value(recipient);
                JO setAccountTokenReturn = context.createTransaction(setAccountToken); //Set the account property on the contract account to mark that view token as redeemed

                //context.logInfoMessage(String.format("INFO: token address: %s | contract address: %s", setAccountTokenReturn, giftAddress));

                SendMoneyCall sendMoneyCall = SendMoneyCall.create(giftType).
                        recipient(recipient).
                        amountNQT(giftAmount);

                return context.createTransaction(sendMoneyCall);
            }
        }
    }
}
