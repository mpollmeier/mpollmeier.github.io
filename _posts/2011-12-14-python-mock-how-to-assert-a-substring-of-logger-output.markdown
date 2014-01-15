---
layout: post
title: ! 'Python Mock: how to assert a substring of logger output'
date: 2011-12-14 00:14:56.000000000 +01:00
permalink: python-mock-how-to-assert-a-substring-of-logger-output
tag:
  - python
  - testing
---
Sometimes you have to write a method that's not supposed to raise any exceptions. One such case was when I integrated dcramers <a href="https://github.com/dcramer/django-paypal">django-paypal</a> and implemented a listener for incoming payment notifications from Paypal. If my listener raises an exception, the payment notification will not be confirmed with Paypal and is therefor being retriggered by them. This is clearly not what I want to happen e.g. when our system can't find the associated invoice, so I'm catching all exceptions and logging error messages which trigger emails to our support team. 

``` python
    def paypal_ipn_parse(sender, **kwargs):
    try:
        #perform checks and mark invoice as paid etc
        ...
    except Exception as e:
        logger.error('caught exception while parsing paypal ipn: %s' % e)
```

There's a couple of 'exceptional situations' that are being checked, e.g. 'can the invoice be found', 'is the payment in the right amount and currency' etc. But if the listener can't raise an exception, how can we unit test this behaviour? The way I went for is to mock the logger instance from the module under test using <a href="http://www.voidspace.org.uk/python/mock/">Python Mock</a> and assert that an expected string has been logged on error level. In order to have the flexibility to change the error message without breaking the test, I wrote a little helper called 'SubstringMatcher' which only checks for a given substring in the logged message:

``` python
from payment.utils import logger as utils_logger

class TestPaypalIpnHandling(TestCase):
    def setUp(self):
        utils_logger.error = Mock()

    def test_should_report_error_on_wrong_currency(self):
        ipn = PayPalIPN(invoice=self.test_invoice_uuid, mc_currency='non_existing_currency')
        paypal_ipn_parse(ipn)
        utils_logger.error.assert_called_with(
            SubstringMatcher(containing='wrong currency'))

from string import lower
class SubstringMatcher():
    def __init__(self, containing):
        self.containing = lower(containing)
    def __eq__(self, other):
        return lower(other).find(self.containing) > -1
    def __unicode__(self):
        return 'a string containing "%s"' % self.containing
    def __str__(self):
        return unicode(self).encode('utf-8')
    __repr__=__unicode__
```

Now our unit test ensures that the mocked method <code>logger.error</code> does log an error message containing the substring <em>wrong currency</em>. Otherwise the unit test error output will state: 
<em>AssertionError: Expected: ((a string containing "wrong currency",), {})</em>
