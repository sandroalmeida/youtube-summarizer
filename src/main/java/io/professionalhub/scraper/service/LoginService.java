package io.professionalhub.scraper.service;

import io.professionalhub.scraper.config.ConfigManager;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Service class responsible for handling LinkedIn login process
 */
public class LoginService {
    private static final ConfigManager config = ConfigManager.getInstance();
    
    /**
     * Performs the complete LinkedIn login process
     *
     * @param context The browser context to use for login
     */
    public void performLogin(BrowserContext context) {
        System.out.println("=== LoginService: Starting login process ===");
        
        Page loginPage = context.newPage();
        
        try {
            // Navigate to LinkedIn login page
            navigateToLoginPage(loginPage);
            
            // Wait for user to complete login manually
            waitForUserLogin(loginPage);
            
            // Verify login success
            verifyLoginSuccess(loginPage);
            
            System.out.println("=== LoginService: Login process completed successfully ===");

        } catch (Exception e) {
            System.err.println("LoginService Error: " + e.getMessage());
            throw new RuntimeException("Failed to complete login process", e);
        }
    }
    
    /**
     * Navigate to LinkedIn login page and wait for form to load
     */
    private void navigateToLoginPage(Page loginPage) {
        System.out.println("Step 1: Opening LinkedIn login page...");
        
        loginPage.navigate(config.getLinkedinLoginUrl());
        
        // Wait for login form to load
        loginPage.waitForSelector("input[name='session_key']", new Page.WaitForSelectorOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(config.getElementWaitTimeout()));
        
        System.out.println("✓ LinkedIn login page loaded successfully!");
        
        if (config.isDebugMode()) {
            System.out.println("Debug: Current URL after navigation: " + loginPage.url());
        }
    }
    
    /**
     * Wait for user to manually complete the login process
     */
    private void waitForUserLogin(Page loginPage) {
        System.out.println("Step 2: Waiting for user login completion...");
        System.out.println("Please enter your credentials in the browser and complete the login process.");
        System.out.println("After logging in, you should be redirected to LinkedIn homepage.");
        
        boolean loginDetected = false;
        
        // Try to detect successful login through URL change
        try {
            // Wait for either the feed page or homepage to load (indicates successful login)
            loginPage.waitForURL("**/feed/**", new Page.WaitForURLOptions()
                .setTimeout(config.getLoginTimeout()));
            System.out.println("✓ Login successful! Detected redirect to LinkedIn feed.");
            loginDetected = true;
            
        } catch (Exception e) {
            if (config.isDebugMode()) {
                System.out.println("Debug: Feed URL not detected, trying alternative detection...");
            }
            
            try {
                // Alternative: wait for any LinkedIn main page
                loginPage.waitForURL("https://www.linkedin.com/**", new Page.WaitForURLOptions()
                    .setTimeout(5000));
                System.out.println("✓ Login appears successful! Detected LinkedIn main page.");
                loginDetected = true;
                
            } catch (Exception e2) {
                System.out.println("⚠ Warning: Could not detect automatic redirect. Assuming login is complete.");
                loginDetected = true; // Assume success for manual verification
            }
        }
        
        if (config.isDebugMode() && loginDetected) {
            System.out.println("Debug: Final URL after login: " + loginPage.url());
        }
    }
    
    /**
     * Verify that login was successful by checking for absence of login elements
     */
    private void verifyLoginSuccess(Page loginPage) {
        System.out.println("Step 3: Verifying login success...");
        
        try {
            // Check if login form is no longer visible
            loginPage.waitForSelector("input[name='session_key']", new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(5000));
            System.out.println("✓ Login form no longer visible - login confirmed!");
            
        } catch (Exception e) {
            System.out.println("⚠ Note: Login form still visible, but proceeding...");
            
            if (config.isDebugMode()) {
                System.out.println("Debug: This might indicate the login process is still in progress");
                System.out.println("Debug: or the page structure has changed");
            }
        }
        
        // Additional verification - check for common post-login elements
        try {
            // Look for navigation elements that appear after login
            boolean hasNavigation = loginPage.locator("nav").count() > 0;
            boolean hasUserMenu = loginPage.locator("[data-control-name*='nav.settings']").count() > 0 ||
                                loginPage.locator(".global-nav__me").count() > 0;
            
            if (hasNavigation || hasUserMenu) {
                System.out.println("✓ Post-login navigation elements detected");
            } else {
                System.out.println("⚠ Post-login elements not clearly detected");
            }
            
            if (config.isDebugMode()) {
                System.out.println("Debug: Navigation elements found: " + hasNavigation);
                System.out.println("Debug: User menu elements found: " + hasUserMenu);
            }
            
        } catch (Exception e) {
            System.out.println("⚠ Could not verify post-login elements");
            if (config.isDebugMode()) {
                System.out.println("Debug: Verification error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if the current page appears to be logged in to LinkedIn
     * 
     * @param page The page to check
     * @return true if appears to be logged in, false otherwise
     */
    public boolean isLoggedIn(Page page) {
        try {
            // Check for absence of login form
            boolean noLoginForm = page.locator("input[name='session_key']").count() == 0;
            
            // Check for presence of navigation or user menu
            boolean hasNavigation = page.locator("nav").count() > 0;
            boolean hasUserMenu = page.locator("[data-control-name*='nav.settings']").count() > 0 ||
                                page.locator(".global-nav__me").count() > 0;
            
            boolean loggedIn = noLoginForm && (hasNavigation || hasUserMenu);
            
            if (config.isDebugMode()) {
                System.out.println("Debug: Login status check - No login form: " + noLoginForm + 
                                 ", Has navigation: " + hasNavigation + 
                                 ", Has user menu: " + hasUserMenu +
                                 ", Overall logged in: " + loggedIn);
            }
            
            return loggedIn;
            
        } catch (Exception e) {
            if (config.isDebugMode()) {
                System.out.println("Debug: Error checking login status: " + e.getMessage());
            }
            return false;
        }
    }
}