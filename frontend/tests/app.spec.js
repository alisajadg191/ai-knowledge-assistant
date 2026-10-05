import { test, expect } from "@playwright/test";

test.beforeEach(async ({ request }) => {
  const docs = await (
    await request.get("http://127.0.0.1:8081/api/documents")
  ).json();
  for (const doc of docs)
    await request.delete(`http://127.0.0.1:8081/api/documents/${doc.id}`);
});
test("upload, cited answer, sources mode, export, deletion and empty library", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByText("Backend connected", { exact: true }),
  ).toBeVisible();
  await page
    .getByLabel("Upload document", { exact: true })
    .setInputFiles({
      name: "runbook.md",
      mimeType: "text/markdown",
      buffer: Buffer.from("Post incident updates every 30 minutes."),
    });
  await expect(page.getByRole("status")).toContainText("indexed");
  await page
    .getByLabel("What would you like to know?")
    .fill("When should incident updates be posted?");
  await page
    .getByRole("button", { name: "Ask your documents →", exact: true })
    .click();
  await expect(page.locator(".answer-text")).toContainText("30 minutes");
  await page.getByRole("button", { name: "View source 1" }).click();
  await expect(page.locator("#source-1")).toHaveClass(/highlight/);
  await expect(page.locator("#source-1 blockquote")).toContainText(
    "30 minutes",
  );
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export JSON ↓" }).click();
  expect((await download).suggestedFilename()).toBe("knowledge-answer.json");
  await page.getByRole("button", { name: "Sources only", exact: true }).click();
  await page
    .getByRole("button", { name: "Ask your documents →", exact: true })
    .click();
  await expect(page.locator(".result-label")).toContainText("SOURCES ONLY");
  await page
    .getByRole("button", { name: "Delete runbook.md", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Delete document", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("deleted");
  await expect(
    page.getByRole("button", { name: "Ask your documents →", exact: true }),
  ).toBeDisabled();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
test("unsupported question abstains and model errors stay visible", async ({
  page,
}) => {
  await page.goto("/");
  await page
    .getByLabel("Upload document", { exact: true })
    .setInputFiles({
      name: "runbook.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("Post updates every 30 minutes."),
    });
  await expect(page.getByRole("status")).toContainText("indexed");
  await page
    .getByLabel("What would you like to know?")
    .fill("What is the salary?");
  await page
    .getByRole("button", { name: "Ask your documents →", exact: true })
    .click();
  await expect(page.locator(".result-label")).toContainText("NO EVIDENCE");
  await page.route("**/api/questions", (r) =>
    r.fulfill({
      status: 502,
      contentType: "application/problem+json",
      body: JSON.stringify({ detail: "Ollama is unavailable." }),
    }),
  );
  await page
    .getByRole("button", { name: "Ask your documents →", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("Ollama is unavailable");
  await expect(page.locator(".answer-panel")).toHaveCount(0);
});
