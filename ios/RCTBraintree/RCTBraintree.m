//
//  RCTBraintree.m
//  RCTBraintree
//
//  Created by Rickard Ekman on 18/06/16.
//  Copyright © 2016 Rickard Ekman. All rights reserved.
//

#import "RCTBraintree.h"
#import "Skillz+DeepLinking.h"

@interface RCTBraintree ()

@property (nonatomic, strong) NSString *URLScheme;

@end


@implementation RCTBraintree

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

+ (instancetype)sharedInstance {
    static RCTBraintree *_sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _sharedInstance = [[RCTBraintree alloc] init];
    });
    return _sharedInstance;
}

- (instancetype)init
{
    if (self = [super init]) {
        self.dataCollector = [[BTDataCollector alloc] initWithEnvironment:BTDataCollectorEnvironmentProduction];
    }
    return self;
}

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(setupWithClientToken:(NSString *)clientToken
                  callback:(RCTResponseSenderBlock)callback)
{
    self.URLScheme = [[Skillz skillzInstance] getPaymentsDeepLinkURLScheme];
    [BTAppSwitch setReturnURLScheme:self.URLScheme];

    self.braintreeClient = [[BTAPIClient alloc] initWithAuthorization:clientToken];

    if (self.braintreeClient == nil) {
        callback(@[@(NO)]);
    }
    else {
        callback(@[@(YES)]);
    }
}


RCT_EXPORT_METHOD(payPalRequestOneTimePayment:(NSString *)amount
                  currencyCode:(NSString *) currencyCode
                  callback:(RCTResponseSenderBlock) callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        BTPayPalDriver *payPalDriver = [[BTPayPalDriver alloc] initWithAPIClient:self.braintreeClient];
        payPalDriver.viewControllerPresentingDelegate = self;
        BTPayPalRequest *request = [[BTPayPalRequest alloc] initWithAmount:amount];
        request.currencyCode = currencyCode; // Optional; see BTPayPalRequest.h for other options

        [payPalDriver requestOneTimePayment:request
                                 completion:^(BTPayPalAccountNonce * _Nullable tokenizedPayPalAccount, NSError * _Nullable error) {
            [self handlePayPalResult:tokenizedPayPalAccount error:error callback:callback];
        }];
    });
}

RCT_EXPORT_METHOD(payPalRequestBillingAgreement:(NSString *)amount
                  currencyCode:(NSString *) currencyCode
                  callback:(RCTResponseSenderBlock) callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        BTPayPalDriver *payPalDriver = [[BTPayPalDriver alloc] initWithAPIClient:self.braintreeClient];
        payPalDriver.viewControllerPresentingDelegate = self;
        BTPayPalRequest *request = [[BTPayPalRequest alloc] initWithAmount:amount];
        request.currencyCode = currencyCode; // Optional; see BTPayPalRequest.h for other options

        [payPalDriver requestBillingAgreement:request
                                   completion:^(BTPayPalAccountNonce * _Nullable tokenizedPayPalAccount, NSError * _Nullable error) {
            [self handlePayPalResult:tokenizedPayPalAccount error:error callback:callback];
        }];
    });
}

- (void)handlePayPalResult:(BTPayPalAccountNonce * _Nullable)tokenizedPayPalAccount
                     error:(NSError * _Nullable)error
                  callback:(RCTResponseSenderBlock)callback
{
    NSMutableArray *args = @[[NSNull null]];

    if (error == nil && tokenizedPayPalAccount != nil) {
        NSString *email = tokenizedPayPalAccount.email;
        NSString *firstName = tokenizedPayPalAccount.firstName;
        NSString *lastName = tokenizedPayPalAccount.lastName;
        NSString *phone = tokenizedPayPalAccount.phone;

        // See BTPostalAddress.h for details
        BTPostalAddress *billingAddress = tokenizedPayPalAccount.billingAddress;
        BTPostalAddress *shippingAddress = tokenizedPayPalAccount.shippingAddress;

        args = [@[[NSNull null], tokenizedPayPalAccount.nonce, email, firstName, lastName] mutableCopy];

        if (tokenizedPayPalAccount.phone != nil) {
            [args addObject:phone];
        }
        if (billingAddress != nil) {
            [args addObject:billingAddress];
        }
        if (shippingAddress != nil) {
            [args addObject:shippingAddress];
        }
    } else if ( error != nil ) {
        args = @[error.description, [NSNull null]];
    } else { // per braintree docs, if both error and token are nil, user cancelled
        args = @[@"USER_CANCELLATION", [NSNull null]];
    }

    callback(args);
}

RCT_EXPORT_METHOD(getCardNonce:(NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback)
{
    NSMutableDictionary *parameters = [params mutableCopy];
    BTCardClient *cardClient = [[BTCardClient alloc] initWithAPIClient:self.braintreeClient];
    BTCard *card = [[BTCard alloc] initWithNumber:parameters[@"number"]
                                  expirationMonth:parameters[@"expirationMonth"]
                                   expirationYear:parameters[@"expirationYear"]
                                              cvv:parameters[@"cvv"]];
    card.shouldValidate = NO;
    [cardClient tokenizeCard:card
                  completion:^(BTCardNonce *tokenizedCard, NSError *error) {
        if (error == nil) {
            callback(@[[NSNull null], tokenizedCard.nonce]);
            return;
        }

        NSArray *args = @[];
        NSMutableDictionary *userInfo = [error.userInfo mutableCopy];

        [userInfo removeObjectForKey:@"com.braintreepayments.BTHTTPJSONResponseBodyKey"];
        [userInfo removeObjectForKey:@"com.braintreepayments.BTHTTPURLResponseKey"];
        NSError *serialisationErr;
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:userInfo
                                                           options:NSJSONWritingPrettyPrinted
                                                             error:&serialisationErr];

        if (!jsonData) {
            args = @[serialisationErr.description, [NSNull null]];
        } else {
            NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
            args = @[jsonString, [NSNull null]];
        }
        callback(args);
    }];
}

RCT_EXPORT_METHOD(getDeviceData:(NSDictionary *)options
                  callback:(RCTResponseSenderBlock)callback)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        NSError *error = nil;
        NSString *deviceData = nil;
        NSString *environment = options[@"environment"];
        NSString *dataSelector = options[@"dataCollector"];

        //Initialize the data collector and specify environment
        if ([environment isEqualToString:@"development"]) {
            self.dataCollector = [[BTDataCollector alloc]  initWithEnvironment:BTDataCollectorEnvironmentDevelopment];
        } else if ([environment isEqualToString:@"qa"]) {
            self.dataCollector = [[BTDataCollector alloc] initWithEnvironment:BTDataCollectorEnvironmentQA];
        } else if ([environment isEqualToString:@"sandbox"]) {
            self.dataCollector = [[BTDataCollector alloc] initWithEnvironment:BTDataCollectorEnvironmentSandbox];
        }

        //Data collection methods
        if ([dataSelector isEqualToString:@"card"]) {
            deviceData = [self.dataCollector collectCardFraudData];
        } else if ([dataSelector isEqualToString:@"both"]) {
            deviceData = [self.dataCollector collectFraudData];
        } else if ([dataSelector isEqualToString:@"paypal"]) {
            deviceData = [PPDataCollector collectPayPalDeviceData];
        } else {
            NSMutableDictionary* details = [NSMutableDictionary dictionary];
            [details setValue:@"Invalid data collector" forKey:NSLocalizedDescriptionKey];
            error = [NSError errorWithDomain:@"RCTBraintree" code:255 userInfo:details];

            SKZLog(@"Invalid data collector: %@. Use one of: `card`, `paypal`, or `both`", dataSelector);
        }

        NSArray *args = @[];
        if (error == nil) {
            args = @[[NSNull null], deviceData];
        } else {
            args = @[error.description, [NSNull null]];
        }

        callback(args);
    });
}

- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options
{
    if ([url.scheme localizedCaseInsensitiveCompare:self.URLScheme] == NSOrderedSame) {
        return [BTAppSwitch handleOpenURL:url options:options];
    }
    return NO;
}

#pragma mark - BTViewControllerPresentingDelegate

- (void)paymentDriver:(id)paymentDriver
requestsPresentationOfViewController:(UIViewController *)viewController
{
    [self.reactRoot presentViewController:viewController animated:YES completion:nil];
}

- (void)paymentDriver:(id)paymentDriver
requestsDismissalOfViewController:(UIViewController *)viewController
{
    if (!self.reactRoot.isBeingDismissed) {
        [self.reactRoot.presentingViewController dismissViewControllerAnimated:YES completion:nil];
    }
}

// #pragma mark - BTDropInViewControllerDelegate

- (void)userDidCancelPayment
{
    [self.reactRoot dismissViewControllerAnimated:YES completion:nil];
    self.callback(@[@"USER_CANCELLATION", [NSNull null]]);
}

- (UIViewController *)reactRoot
{
    UIViewController *root = [UIApplication sharedApplication].keyWindow.rootViewController;
    while (root.presentedViewController) {
        root = root.presentedViewController;
    }

    return root;
}

@end
