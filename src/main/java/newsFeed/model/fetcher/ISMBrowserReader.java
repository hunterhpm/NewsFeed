package newsFeed.model.fetcher;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reads ISM report pages through a persistent real Chrome session.
 *
 * The first request opens the ISM page normally.
 * Later requests use fetch() inside that existing Chrome session.
 */
public final class ISMBrowserReader {

    private static final ExecutorService OWNER =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ism-browser");
                thread.setDaemon(true);
                return thread;
            });

    private static final Map<String, Page> PAGES =
            new HashMap<>();

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;

    private ISMBrowserReader() {
    }

    public static String read(String url) {

        return await(OWNER.submit(() -> {
            ensureBrowser();

            Page page = PAGES.get(url);

            if (page == null || page.isClosed()) {
                return initialLoad(url);
            }

            return browserFetch(page, url);
        }));
    }

    private static String initialLoad(String url) {

        Page page = context.newPage();
        PAGES.put(url, page);

        long start = System.nanoTime();

        Response response = page.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
        );

        if (response == null) {
            throw new IllegalStateException(
                    "ISM browser did not return a response."
            );
        }

        /*
         * ISM may first return an invisible reCAPTCHA page.
         *
         * The challenge normally runs automatically. Do not grab
         * page.content() merely because the CAPTCHA disappeared:
         * during the redirect there can be a temporary incomplete page.
         *
         * Instead, wait until the actual ISM report text exists.
         */
        if (isCaptcha(page.content())) {
            System.out.println(
                    "ISM verification detected. Waiting for report..."
            );
        }

        waitForReport(page, url);

        String html = page.content();

        if (isCaptcha(html)) {
            throw new IllegalStateException(
                    "ISM verification did not complete."
            );
        }

        long end = System.nanoTime();

        System.out.printf(
                Locale.ROOT,
                "ISM initial browser load: %.3f seconds%n",
                (end - start) / 1_000_000_000.0
        );

        return html;
    }

    private static void waitForReport(
            Page page,
            String url) {

        String expectedText;

        if (url.contains("/services/")) {
            expectedText = "Services PMI";
        } else {
            expectedText = "Manufacturing PMI";
        }

        page.waitForFunction(
                """
                expectedText => {
                    if (!document.body) {
                        return false;
                    }

                    const text =
                        (document.body.innerText || '')
                            .replace(/\\s+/g, ' ')
                            .trim();

                    return text.includes(expectedText)
                        && text.length > 500;
                }
                """,
                expectedText,
                new Page.WaitForFunctionOptions()
                        .setTimeout(120_000)
        );
    }

    private static String browserFetch(
            Page page,
            String url) {

        long javaStart = System.nanoTime();

        Object result = page.evaluate(
                """
                async url => {
                    const start = performance.now();

                    const response = await fetch(url, {
                        method: 'GET',
                        cache: 'no-store',
                        credentials: 'include'
                    });

                    const responseReceived =
                        performance.now();

                    const html =
                        await response.text();

                    const finished =
                        performance.now();

                    return {
                        status:
                            response.status,

                        responseMilliseconds:
                            responseReceived - start,

                        bodyMilliseconds:
                            finished - responseReceived,

                        totalMilliseconds:
                            finished - start,

                        html:
                            html
                    };
                }
                """,
                url
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> data =
                (Map<String, Object>) result;

        int status =
                ((Number) data.get("status"))
                        .intValue();

        String html =
                (String) data.get("html");

        if (status != 200) {
            throw new IllegalStateException(
                    "ISM browser fetch returned HTTP "
                            + status
            );
        }

        if (isCaptcha(html)) {
            throw new IllegalStateException(
                    "ISM returned verification HTML "
                            + "during browser fetch."
            );
        }

        double responseSeconds =
                ((Number) data.get(
                        "responseMilliseconds"
                )).doubleValue() / 1000.0;

        double bodySeconds =
                ((Number) data.get(
                        "bodyMilliseconds"
                )).doubleValue() / 1000.0;

        double browserSeconds =
                ((Number) data.get(
                        "totalMilliseconds"
                )).doubleValue() / 1000.0;

        double javaSeconds =
                (System.nanoTime() - javaStart)
                        / 1_000_000_000.0;

        System.out.println(
                "ISM browser fetch:"
        );

        System.out.printf(
                Locale.ROOT,
                "  response:   %.3f seconds%n",
                responseSeconds
        );

        System.out.printf(
                Locale.ROOT,
                "  body:       %.3f seconds%n",
                bodySeconds
        );

        System.out.printf(
                Locale.ROOT,
                "  browser:    %.3f seconds%n",
                browserSeconds
        );

        System.out.printf(
                Locale.ROOT,
                "  Java total: %.3f seconds%n",
                javaSeconds
        );

        return html;
    }

    private static boolean isCaptcha(
            String html) {

        if (html == null) {
            return false;
        }

        return html.contains("captcha_form")
                || html.contains("recaptcha/api.js")
                || html.contains("grecaptcha.execute");
    }

    private static void ensureBrowser() {

        if (playwright != null) {
            return;
        }

        long start =
                System.nanoTime();

        try {
            playwright =
                    Playwright.create();

            browser =
                    playwright.chromium().launch(
                            new BrowserType
                                    .LaunchOptions()
                                    .setChannel("chrome")
                                    .setHeadless(false)
                    );

            context =
                    browser.newContext();

            context.setDefaultTimeout(
                    20_000
            );

        } catch (RuntimeException e) {

            closeOnOwner();

            throw new IllegalStateException(
                    "Could not start Chrome for ISM.",
                    e
            );
        }

        System.out.printf(
                Locale.ROOT,
                "ISM browser startup: %.3f seconds%n",
                (System.nanoTime() - start)
                        / 1_000_000_000.0
        );
    }

    public static void close() {

        await(OWNER.submit(() -> {
            closeOnOwner();
            return null;
        }));
    }

    private static void closeOnOwner() {

        for (Page page : PAGES.values()) {

            try {
                if (!page.isClosed()) {
                    page.close();
                }

            } catch (RuntimeException ignored) {
                // Continue closing resources.
            }
        }

        PAGES.clear();

        try {
            if (context != null) {
                context.close();
            }

        } finally {

            try {
                if (browser != null) {
                    browser.close();
                }

            } finally {

                if (playwright != null) {
                    playwright.close();
                }

                context = null;
                browser = null;
                playwright = null;
            }
        }
    }

    private static <T> T await(
            Future<T> task) {

        try {
            return task.get();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "ISM browser request interrupted.",
                    e
            );

        } catch (ExecutionException e) {

            if (e.getCause()
                    instanceof RuntimeException cause) {

                throw cause;
            }

            throw new IllegalStateException(
                    "ISM browser request failed.",
                    e.getCause()
            );
        }
    }
}