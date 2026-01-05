# Professional Hub Scraper
This project is the attempt to create the simplest Java application to scrape Linkedin Profiles.
The goal is fetching profiles directly from Linkedin Recruiter and save them locally in JSON format.

# Architecture Decisions
This application don't connect to Databases, relying only on the configurations file.
Another file will be created to manage the profiles already scraped.

# Configurations
browser.connect.to.running=true - is used to connect to a running browser instance.
When using the option above execute the browser with the following command:
```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --profile-directory="Profile 8" --user-data-dir="$HOME/chrome-debug-profile"  --remote-debugging-port=9222
```
To test with the browser was initialized correctly, you can use the following command:
```bash
curl http://localhost:9222/json/version
```
A message like this should be returned:
```json
{
 "Browser": "Chrome/137.0.7151.122",
 "Protocol-Version": "1.3",
 "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
 "V8-Version": "13.7.152.17",
 "WebKit-Version": "537.36 (@4c03e725fa2c140a1606dad6fef5158687287c83)",
 "webSocketDebuggerUrl": "ws://localhost:9222/devtools/browser/25d32b12-da16-4d94-bd27-44a0b8c087f1"
}
```

browser.use.existing.profile=false - is used to launch a new browser instance.
browser.chrome.profile.dir=Profile 8 - It was created a copy of the Default Chrome Profile to be used by the application.
browser.skip.login=true - If set to true, the application will not attempt to log in to Linkedin.
