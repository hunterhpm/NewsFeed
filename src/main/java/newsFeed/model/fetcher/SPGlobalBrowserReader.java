package newsFeed.model.fetcher;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reads S&P Global PMI pages through a persistent Chrome session.
 *
 * The release-list page can use a fast browser-side fetch after the
 * browser session has been established.
 *
 * Individual press-release pages use normal Chrome navigation because
 * S&P Global appears to populate the actual PMI report content after
 * the initial HTML response.
 */
public final class SPGlobalBrowserReader {

    private static final ExecutorService OWNER =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread =
                        new Thread(r, "spglobal-browser");

                thread.setDaemon(true);

                return thread;
            });

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;
    private static Page page;
    private static boolean shutdownHookRegistered;

    private SPGlobalBrowserReader() {
    }

    public static String read(String url) {

        validateUrl(url);

        return await(
                OWNER.submit(() -> {
                    ensureBrowser();

                    /*
                     * First request establishes the real S&P Global
                     * Chrome session and handles verification.
                     */
                    if (page == null || page.isClosed()) {

                        page = context.newPage();

                        return navigateAndRead(url);
                    }

                    /*
                     * Individual press releases are PDFs.
                     *
                     * Do NOT navigate Chrome's PDF viewer and then
                     * look for document.body. Fetch the PDF bytes
                     * directly through the established browser session.
                     */
                    if (url.contains(
                            "/Public/Home/PressRelease/"
                    )) {

                        return browserFetchPdfText(url);
                    }

                    /*
                     * Normal HTML pages can use the lightweight
                     * browser-side fetch.
                     */
                    return browserFetch(url);
                })
        );
    }

    private static String navigateAndRead(
            String url) {

        long start =
                System.nanoTime();

        Response response =
                page.navigate(
                        url,
                        new Page.NavigateOptions()
                                .setWaitUntil(
                                        WaitUntilState.DOMCONTENTLOADED
                                )
                );

        if (response == null) {

            throw new IllegalStateException(
                    "S&P Global browser did not return a response."
            );
        }

        int status =
                response.status();

        /*
         * S&P Global can return HTTP 202 while Chrome is
         * completing its normal verification process.
         *
         * That is not treated as a failure.
         */
        if (status != 200 && status != 202) {

            throw new IllegalStateException(
                    "S&P Global browser returned HTTP "
                            + status
                            + " for "
                            + url
            );
        }

        if (status == 202) {

            System.out.println(
                    "S&P Global returned HTTP 202. "
                            + "Waiting for real page..."
            );
        }

        /*
         * Do not return page.content() until the actual
         * information needed from this page is present.
         */
        waitForRealContent(url);

        String html =
                page.content();

        double seconds =
                (System.nanoTime() - start)
                        / 1_000_000_000.0;

        System.out.printf(
                Locale.ROOT,
                "S&P Global browser navigation: %.3f seconds%n",
                seconds
        );

        return html;
    }

    private static void waitForRealContent(
            String url) {

        if (url.contains(
                "/Public/Release/PressReleases"
        )) {

            System.out.println(
                    "Waiting for S&P Global release list..."
            );

            page.waitForFunction(
                    """
                    () => {
                        if (!document.body) {
                            return false;
                        }
    
                        const text =
                            (document.body.innerText || '')
                                .replace(/\\s+/g, ' ')
                                .trim();
    
                        return text.includes(
                            'S&P Global Flash US PMI'
                        );
                    }
                    """,
                    null,
                    new Page.WaitForFunctionOptions()
                            .setTimeout(120_000)
            );

            return;
        }

        page.waitForLoadState();
    }

    private static String browserFetch(
            String url) {

        long javaStart =
                System.nanoTime();

        Object result =
                page.evaluate(
                        """
                        async url => {

                            const start =
                                performance.now();

                            const response =
                                await fetch(url, {
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

        /*
         * If the lightweight fetch itself gets a verification
         * response, use normal Chrome navigation instead.
         */
        if (status == 202) {

            System.out.println(
                    "S&P Global browser fetch returned 202. "
                            + "Using normal browser navigation..."
            );

            return navigateAndRead(url);
        }

        if (status != 200) {

            throw new IllegalStateException(
                    "S&P Global browser fetch returned HTTP "
                            + status
                            + " for "
                            + url
            );
        }

        String html =
                (String) data.get("html");

        double responseSeconds =
                ((Number) data.get(
                        "responseMilliseconds"
                )).doubleValue()
                        / 1000.0;

        double bodySeconds =
                ((Number) data.get(
                        "bodyMilliseconds"
                )).doubleValue()
                        / 1000.0;

        double browserSeconds =
                ((Number) data.get(
                        "totalMilliseconds"
                )).doubleValue()
                        / 1000.0;

        double javaSeconds =
                (System.nanoTime()
                        - javaStart)
                        / 1_000_000_000.0;

        System.out.println(
                "S&P Global browser fetch:"
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

    private static void validateUrl(
            String url) {

        if (!url.startsWith(
                "https://www.pmi.spglobal.com/"
        )) {

            throw new IllegalArgumentException(
                    "Unsupported S&P Global URL: "
                            + url
            );
        }
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
                    playwright
                            .chromium()
                            .launch(
                                    new BrowserType
                                            .LaunchOptions()
                                            .setChannel(
                                                    "chrome"
                                            )
                                            .setHeadless(
                                                    false
                                            )
                            );

            context =
                    browser.newContext();

            context.setDefaultTimeout(
                    20_000
            );

        } catch (RuntimeException e) {

            closeOnOwner();

            throw new IllegalStateException(
                    "Could not start Chrome for S&P Global.",
                    e
            );
        }

        System.out.printf(
                Locale.ROOT,
                "S&P Global browser startup: %.3f seconds%n",
                (System.nanoTime() - start)
                        / 1_000_000_000.0
        );
    }

    public static void close() {

        await(
                OWNER.submit(() -> {

                    closeOnOwner();

                    return null;
                })
        );
    }

    private static void closeOnOwner() {

        try {

            if (page != null
                    && !page.isClosed()) {

                page.close();
            }

        } catch (RuntimeException ignored) {

            // Continue closing resources.
        }

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

                page = null;
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

            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "S&P Global browser request interrupted.",
                    e
            );

        } catch (ExecutionException e) {

            if (e.getCause()
                    instanceof RuntimeException cause) {

                throw cause;
            }

            throw new IllegalStateException(
                    "S&P Global browser request failed.",
                    e.getCause()
            );
        }
    }

    private static void registerShutdownHook() {

        if (shutdownHookRegistered) {
            return;
        }

        shutdownHookRegistered = true;

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    try {
                        close();
                    } catch (RuntimeException ignored) {
                        // JVM is already shutting down.
                    }
                }, "spglobal-browser-shutdown")
        );
    }

    private static String browserFetchPdfText(
            String url) {

        long javaStart =
                System.nanoTime();

        Object result =
                page.evaluate(
                        """
                        async url => {
    
                            const start =
                                performance.now();
    
                            const response =
                                await fetch(url, {
                                    method: 'GET',
                                    cache: 'no-store',
                                    credentials: 'include'
                                });
    
                            const responseReceived =
                                performance.now();
    
                            const buffer =
                                await response.arrayBuffer();
    
                            const bodyReceived =
                                performance.now();
    
                            const bytes =
                                new Uint8Array(buffer);
    
                            /*
                             * Convert the PDF bytes to Base64 safely.
                             *
                             * Do it in chunks so a large PDF does not
                             * overflow JavaScript's argument limit.
                             */
                            let binary = '';
    
                            const chunkSize = 0x8000;
    
                            for (
                                let i = 0;
                                i < bytes.length;
                                i += chunkSize
                            ) {
    
                                const chunk =
                                    bytes.subarray(
                                        i,
                                        Math.min(
                                            i + chunkSize,
                                            bytes.length
                                        )
                                    );
    
                                binary +=
                                    String.fromCharCode(
                                        ...chunk
                                    );
                            }
    
                            const base64 =
                                btoa(binary);
    
                            const finished =
                                performance.now();
    
                            return {
                                status:
                                    response.status,
    
                                contentType:
                                    response.headers.get(
                                        'content-type'
                                    ) || '',
    
                                responseMilliseconds:
                                    responseReceived - start,
    
                                bodyMilliseconds:
                                    bodyReceived
                                    - responseReceived,
    
                                totalMilliseconds:
                                    finished - start,
    
                                base64:
                                    base64
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

        if (status != 200) {

            throw new IllegalStateException(
                    "S&P Global PDF fetch returned HTTP "
                            + status
                            + " for "
                            + url
            );
        }

        String base64 =
                (String) data.get("base64");

        byte[] bytes =
                Base64.getDecoder()
                        .decode(base64);

        /*
         * Verify that S&P really returned a PDF rather
         * than another verification/error page.
         */
        if (!isPdf(bytes)) {

            String preview =
                    new String(
                            bytes,
                            0,
                            Math.min(
                                    bytes.length,
                                    500
                            ),
                            StandardCharsets.UTF_8
                    );

            throw new IllegalStateException(
                    "S&P Global did not return a PDF. "
                            + "Content-Type: "
                            + data.get("contentType")
                            + ". Response begins: "
                            + preview
                            .replaceAll(
                                    "\\s+",
                                    " "
                            )
            );
        }

        String pdfText =
                extractPdfText(bytes);

        double responseSeconds =
                ((Number) data.get(
                        "responseMilliseconds"
                )).doubleValue()
                        / 1000.0;

        double bodySeconds =
                ((Number) data.get(
                        "bodyMilliseconds"
                )).doubleValue()
                        / 1000.0;

        double browserSeconds =
                ((Number) data.get(
                        "totalMilliseconds"
                )).doubleValue()
                        / 1000.0;

        double javaSeconds =
                (System.nanoTime()
                        - javaStart)
                        / 1_000_000_000.0;

        System.out.println(
                "S&P Global PDF fetch:"
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

        return pdfText;
    }

    private static String extractPdfText(
            byte[] pdfBytes) {

        try (
                PDDocument document =
                        Loader.loadPDF(pdfBytes)
        ) {

            PDFTextStripper stripper =
                    new PDFTextStripper();

            return stripper.getText(document);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Could not extract text from "
                            + "S&P Global PDF.",
                    e
            );
        }
    }

    private static boolean isPdf(
            byte[] bytes) {

        return bytes != null
                && bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }
}