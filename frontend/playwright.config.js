import { defineConfig, devices } from "@playwright/test";
if (!process.env.DATABASE_URL?.split("?")[0].endsWith("_test"))
  throw new Error(
    "Browser tests require DATABASE_URL pointing to a disposable database ending in _test.",
  );
export default defineConfig({
  testDir: "./tests",
  workers: 1,
  retries: 0,
  timeout: 30000,
  use: { baseURL: "http://127.0.0.1:5174", trace: "retain-on-failure" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    {
      name: "mobile",
      use: { ...devices["iPhone 13"], defaultBrowserType: "chromium" },
    },
  ],
  webServer: [
    {
      command: "python3 ../scripts/ollama_stub.py",
      url: "http://127.0.0.1:11435",
      ignoreHTTPSErrors: true,
      reuseExistingServer: false,
      timeout: 20000,
    },
    {
      command: "java -jar ../target/ai-knowledge-assistant-1.0.0.jar",
      url: "http://127.0.0.1:8081/api/status",
      env: { OLLAMA_BASE_URL: "http://127.0.0.1:11435" },
      reuseExistingServer: false,
      timeout: 60000,
    },
    {
      command: "npm run dev",
      url: "http://127.0.0.1:5174",
      reuseExistingServer: false,
      timeout: 30000,
    },
  ],
});
