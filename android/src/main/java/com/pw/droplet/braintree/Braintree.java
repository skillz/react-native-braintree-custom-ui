package com.pw.droplet.braintree;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.exceptions.BraintreeError;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Braintree extends ReactContextBaseJavaModule {
    private String token;

    private Callback successCallback;
    private Callback errorCallback;

    private BraintreeFragment mBraintreeFragment;

    public Braintree(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override @Nonnull
    public String getName() {
        return "Braintree";
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @ReactMethod
    public void setup(final String url, final Callback successCallback, final Callback errorCallback) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                errorCallback.invoke("BRAINTREE_SETUP_EMPTY_RESPONSE_BODY");
                return;
            }

            if (getCurrentActivity() == null) {
                errorCallback.invoke("BRAINTREE_SETUP_NULL_ACTIVITY");
                return;
            }

            this.mBraintreeFragment = BraintreeFragment.newInstance((AppCompatActivity) getCurrentActivity(), responseBody.string());
        } catch (Exception e) {
            errorCallback.invoke(e.getMessage());
        }

        if (this.mBraintreeFragment != null) {
            this.mBraintreeFragment.addListener(new BraintreeCancelListener() {
                @Override
                public void onCancel(int requestCode) {
                    nonceErrorCallback("USER_CANCELLATION");
                }
            });
            this.mBraintreeFragment.addListener(new PaymentMethodNonceCreatedListener() {
                @Override
                public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
                    nonceCallback(paymentMethodNonce.getNonce());
                }
            });

            this.mBraintreeFragment.addListener(new BraintreeErrorListener() {
                @Override
                public void onError(Exception error) {
                    if (error instanceof ErrorWithResponse) {
                        ErrorWithResponse errorWithResponse = (ErrorWithResponse) error;
                        BraintreeError cardErrors = errorWithResponse.errorFor("creditCard");
                        if (cardErrors != null) {
                            Gson gson = new GsonBuilder().create();
                            final Map<String, String> errors = new HashMap<>();
                            BraintreeError numberError = cardErrors.errorFor("number");
                            BraintreeError cvvError = cardErrors.errorFor("cvv");
                            BraintreeError expirationDateError = cardErrors.errorFor("expirationDate");
                            BraintreeError postalCode = cardErrors.errorFor("postalCode");

                            if (numberError != null && numberError.getMessage() != null) {
                                errors.put("card_number", numberError.getMessage());
                            }

                            if (cvvError != null && cvvError.getMessage() != null) {
                                errors.put("cvv", cvvError.getMessage());
                            }

                            if (expirationDateError != null && expirationDateError.getMessage() != null) {
                                errors.put("expiration_date", expirationDateError.getMessage());
                            }

                            if (postalCode != null && postalCode.getMessage() != null) {
                                errors.put("postal_code", postalCode.getMessage());
                            }

                            nonceErrorCallback(gson.toJson(errors));
                        } else {
                            nonceErrorCallback(errorWithResponse.getErrorResponse());
                        }
                    }
                }
            });
            this.setToken(token);
            successCallback.invoke(this.getToken());
        }
    }

    @ReactMethod
    public void getCardNonce(final ReadableMap parameters, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;

        CardBuilder cardBuilder = new CardBuilder()
                .validate(false);

        if (parameters.hasKey("number")) {
            cardBuilder.cardNumber(parameters.getString("number"));
        }

        if (parameters.hasKey("cvv")) {
            cardBuilder.cvv(parameters.getString("cvv"));
        }

        if (parameters.hasKey("expirationDate")) {
            cardBuilder.expirationDate(parameters.getString("expirationDate"));
        } else {
            if (parameters.hasKey("expirationMonth")) {
                cardBuilder.expirationMonth(parameters.getString("expirationMonth"));
            }

            if (parameters.hasKey("expirationYear")) {
                cardBuilder.expirationYear(parameters.getString("expirationYear"));
            }
        }

        if (parameters.hasKey("cardholderName")) {
            cardBuilder.cardholderName(parameters.getString("cardholderName"));
        }

        if (parameters.hasKey("firstName")) {
            cardBuilder.firstName(parameters.getString("firstName"));
        }

        if (parameters.hasKey("lastName")) {
            cardBuilder.lastName(parameters.getString("lastName"));
        }

        if (parameters.hasKey("countryCode")) {
            cardBuilder.countryCode(parameters.getString("countryCode"));
        }

        if (parameters.hasKey("locality")) {
            cardBuilder.locality(parameters.getString("locality"));
        }

        if (parameters.hasKey("postalCode")) {
            cardBuilder.postalCode(parameters.getString("postalCode"));
        }

        if (parameters.hasKey("region")) {
            cardBuilder.region(parameters.getString("region"));
        }

        if (parameters.hasKey("streetAddress")) {
            cardBuilder.streetAddress(parameters.getString("streetAddress"));
        }

        if (parameters.hasKey("extendedAddress")) {
            cardBuilder.extendedAddress(parameters.getString("extendedAddress"));
        }
    }

    private void nonceCallback(String nonce) {
        this.successCallback.invoke(nonce);
    }

    private void nonceErrorCallback(String error) {
        this.errorCallback.invoke(error);
    }

    @ReactMethod
    public void payPalRequestOneTimePayment(final String amount, final String currencyCode, final Callback successCallback, final Callback errorCallback) {
        PayPal.requestOneTimePayment(this.mBraintreeFragment, getPayPalRequest(amount, currencyCode, successCallback, errorCallback));
    }

    @ReactMethod
    public void payPalRequestBillingAgreement(final String amount, final String currencyCode, final Callback successCallback, final Callback errorCallback) {
        PayPal.requestBillingAgreement(this.mBraintreeFragment, getPayPalRequest(amount, currencyCode, successCallback, errorCallback));
    }

    private PayPalRequest getPayPalRequest(final String amount, final String currencyCode, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
        return new PayPalRequest(amount)
                .currencyCode(currencyCode)
                .intent(PayPalRequest.INTENT_AUTHORIZE);
    }

    public void onNewIntent(Intent intent) {
    }
}
