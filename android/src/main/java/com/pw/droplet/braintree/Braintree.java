package com.pw.droplet.braintree;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.DataCollector;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.exceptions.BraintreeError;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.PostalAddress;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    private void cleanupBraintreeFragment() {
        if (this.mBraintreeFragment == null) {
            return;
        }

        this.mBraintreeFragment.removeListener(mCancelListener);
        this.mBraintreeFragment.removeListener(mPaymentNonceCreatedListener);
        this.mBraintreeFragment.removeListener(mErrorListener);
        this.mBraintreeFragment = null;
    }

    @ReactMethod
    public void setup(final String token, final Callback successCallback, final Callback errorCallback) {
        this.cleanupBraintreeFragment();
        try {
            this.setToken(token);
            this.mBraintreeFragment = BraintreeFragment.newInstance((AppCompatActivity) getCurrentActivity(), getToken());
        } catch (Exception e) {
            errorCallback.invoke(e.getMessage());
            return;
        }

        if (this.mBraintreeFragment != null) {
            this.mBraintreeFragment.addListener(mCancelListener);
            this.mBraintreeFragment.addListener(mPaymentNonceCreatedListener);
            this.mBraintreeFragment.addListener(mErrorListener);
            successCallback.invoke();
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

        Card.tokenize(this.mBraintreeFragment, cardBuilder);
    }

    @ReactMethod
    public void payPalRequestOneTimePayment(final String amount, final String currencyCode, final Callback successCallback, final Callback errorCallback) {
        PayPal.requestOneTimePayment(this.mBraintreeFragment, getPayPalRequest(amount, currencyCode, successCallback, errorCallback));
    }

    @ReactMethod
    public void payPalRequestBillingAgreement(final String amount, final String currencyCode, final Callback successCallback, final Callback errorCallback) {
        PayPal.requestBillingAgreement(this.mBraintreeFragment, getPayPalRequest(null, currencyCode, successCallback, errorCallback));
    }

    @ReactMethod
    public void getDeviceData(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {
        final String collectorType = options.hasKey("dataCollector") ? options.getString("dataCollector") : null;

        final BraintreeResponseListener<String> listener = new BraintreeResponseListener<String>() {
            @Override
            public void onResponse(String deviceData) {
                successCallback.invoke(deviceData);
            }
        };

        if (collectorType != null) {
            switch (collectorType) {
                case "card":
                case "both":
                    DataCollector.collectDeviceData(this.mBraintreeFragment, listener);
                    break;
                case "paypal":
                    DataCollector.collectPayPalDeviceData(this.mBraintreeFragment, listener);
                    break;
                default:
                    errorCallback.invoke("Invalid data collector");
            }
        } else {
            errorCallback.invoke("Invalid data collector");
        }
    }

    private void payPalNonceCallback(PayPalAccountNonce payPalAccountNonce) {
        WritableNativeMap map = new WritableNativeMap();
        map.putString("nonce", payPalAccountNonce.getNonce());
        map.putString("firstName", payPalAccountNonce.getFirstName());
        map.putString("lastName", payPalAccountNonce.getLastName());

        if (payPalAccountNonce.getBillingAddress() != null) {
            map.putMap("billingAddress", getPayPalAddressMap(payPalAccountNonce.getBillingAddress()));
        }

        if (payPalAccountNonce.getShippingAddress() != null) {
            map.putMap("shippingAddress", getPayPalAddressMap(payPalAccountNonce.getShippingAddress()));
        }

        this.successCallback.invoke(map);
    }

    private void nonceCallback(String nonce) {
        this.successCallback.invoke(nonce);
    }

    private void nonceErrorCallback(String error) {
        this.errorCallback.invoke(error);
    }

    private PayPalRequest getPayPalRequest(final @Nullable String amount, final String currencyCode, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
        PayPalRequest request = amount != null ? new PayPalRequest(amount) : new PayPalRequest();
        return request
                .currencyCode(currencyCode)
                .intent(PayPalRequest.INTENT_AUTHORIZE);
    }

    private WritableMap getPayPalAddressMap(PostalAddress address) {
        WritableNativeMap map = new WritableNativeMap();
        map.putString("recipientName", address.getRecipientName());
        map.putString("streetAddress", address.getStreetAddress());
        map.putString("extendedAddress", address.getExtendedAddress());
        map.putString("locality", address.getLocality());
        map.putString("countryCodeAlpha2", address.getCountryCodeAlpha2());
        map.putString("postalCode", address.getPostalCode());
        map.putString("region", address.getRegion());
        return map;
    }

    private BraintreeCancelListener mCancelListener = new BraintreeCancelListener() {
        @Override
        public void onCancel(int requestCode) {
            nonceErrorCallback("USER_CANCELLATION");
        }
    };

    private PaymentMethodNonceCreatedListener mPaymentNonceCreatedListener = new PaymentMethodNonceCreatedListener() {
        @Override
        public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
            if (paymentMethodNonce instanceof PayPalAccountNonce) {
                payPalNonceCallback((PayPalAccountNonce)paymentMethodNonce);
            } else {
                nonceCallback(paymentMethodNonce.getNonce());
            }
        }
    };

    private BraintreeErrorListener mErrorListener = new BraintreeErrorListener() {
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
            } else {
                nonceErrorCallback(error.toString());
            }
        }
    };
}
