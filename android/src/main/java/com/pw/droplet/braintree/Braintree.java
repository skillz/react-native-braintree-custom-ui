package com.pw.droplet.braintree;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.DataCollector;
import com.braintreepayments.api.GooglePayment;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.exceptions.BraintreeError;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.ConfigurationListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.GooglePaymentCardNonce;
import com.braintreepayments.api.models.GooglePaymentConfiguration;
import com.braintreepayments.api.models.GooglePaymentRequest;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.PostalAddress;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Braintree extends ReactContextBaseJavaModule {
    private static final String TAG = "BraintreeRNModule";
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
            this.mBraintreeFragment.addListener(mConfigurationListener);
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
    public void googlePayIsReadyToPay(final Callback successCallback, final Callback errorCallback) {
        try {
            GooglePayment.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
                @Override
                public void onResponse(Boolean isReadyToPay) {
                    successCallback.invoke(isReadyToPay);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error calling GooglePayment.isReadyToPay", e);
            errorCallback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void googlePayRequestPayment(final String googlePayMerchantId, final String amount, final String currencyCode, final boolean billingAddressRequired, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;

        GooglePaymentRequest request = new GooglePaymentRequest()
                .transactionInfo(TransactionInfo.newBuilder()
                        .setTotalPrice(amount) // format: 0.00
                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                        .setCurrencyCode(currencyCode) // ISO 4217 currency code
                        .build());

        request.billingAddressRequired(billingAddressRequired);
        //request.setAllowedCardNetworks(); TODO: pass down array of allowed card networks
        //request.allowPrepaidCards(); TODO: add as parameter
        //request.environment(); TODO: add as parameter (PRODUCTION or TEST)

        if (googlePayMerchantId != null && googlePayMerchantId.length() > 0) {
            // Optional in sandbox; if set in sandbox, this value must be a valid production Google Merchant ID
            request.googleMerchantId(googlePayMerchantId);
        }

        GooglePayment.requestPayment(mBraintreeFragment, request);
    }

    private void googlePayNonceCallback(GooglePaymentCardNonce googlePayNonce) {
        if (googlePayNonce == null || googlePayNonce.getNonce() == null) {
            this.errorCallback.invoke("GooglePay nonce is null");
            this.errorCallback = null;
            this.successCallback = null;
            return;
        }

        WritableNativeMap map = new WritableNativeMap();
        map.putString("nonce", googlePayNonce.getNonce());

        if (googlePayNonce.getCardType() == null) {
            this.errorCallback.invoke("GooglePay cardType is null");
            this.errorCallback = null;
            this.successCallback = null;
            return;
        }
        map.putString("cardType", googlePayNonce.getCardType());

        if (googlePayNonce.getLastFour() != null) {
            map.putString("lastFour", googlePayNonce.getLastFour());
        }

        if (googlePayNonce.getEmail() != null) {
            map.putString("email", googlePayNonce.getEmail());
        }

        if (googlePayNonce.getBillingAddress() != null) {
            map.putMap("billingAddress", getAddressMap(googlePayNonce.getBillingAddress()));
        }

        if (googlePayNonce.getShippingAddress() != null) {
            map.putMap("shippingAddress", getAddressMap(googlePayNonce.getShippingAddress()));
        }

        this.successCallback.invoke(map);
        this.errorCallback = null;
        this.successCallback = null;
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
            map.putMap("billingAddress", getAddressMap(payPalAccountNonce.getBillingAddress()));
        }

        if (payPalAccountNonce.getShippingAddress() != null) {
            map.putMap("shippingAddress", getAddressMap(payPalAccountNonce.getShippingAddress()));
        }

        this.successCallback.invoke(map);
    }

    private void nonceCallback(String nonce) {
        if (this.successCallback != null) {
            this.successCallback.invoke(nonce);
            this.successCallback = null;
        } else {
            Log.e(TAG, "Braintree successCallback is null!");
        }
    }

    private void nonceErrorCallback(String error) {
        if (this.errorCallback != null) {
            this.errorCallback.invoke(error);
            this.errorCallback = null;
        } else {
            Log.e(TAG, "Braintree errorCallback is null!");
        }
    }

    private PayPalRequest getPayPalRequest(final @Nullable String amount, final String currencyCode, final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
        PayPalRequest request = amount != null ? new PayPalRequest(amount) : new PayPalRequest();
        return request
                .currencyCode(currencyCode)
                .intent(PayPalRequest.INTENT_AUTHORIZE);
    }

    private ReadableMap getAddressMap(PostalAddress address) {
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

    private ConfigurationListener mConfigurationListener = new ConfigurationListener() {
        @Override
        public void onConfigurationFetched(Configuration configuration) {
            GooglePaymentConfiguration gPayConfig = configuration.getGooglePayment();
            if (gPayConfig != null) {
                // TODO remove all these logs before going live
                Log.d(TAG, "GooglePay environment: " + gPayConfig.getEnvironment());
                Log.d(TAG, "GooglePay display name: " + gPayConfig.getDisplayName());
                Log.d(TAG, "GooglePay authorization fingerprint: " + gPayConfig.getGoogleAuthorizationFingerprint());
                if (gPayConfig.getSupportedNetworks() != null) {
                    for (String network : gPayConfig.getSupportedNetworks()) {
                        Log.d(TAG, "GooglePay supported network: " + network);
                    }
                }
            }
            else {
                Log.w(TAG, "GooglePay configuration is null");
            }
        }
    };

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
            } else if (paymentMethodNonce instanceof GooglePaymentCardNonce) {
                googlePayNonceCallback((GooglePaymentCardNonce)paymentMethodNonce);
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
                Log.e(TAG, "Unknown Braintree exception", error);
            }
        }
    };
}
