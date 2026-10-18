// Controlled API fixture for the real React Router app. No product backend is started.
// Run with the task-owned frontend URL: NODE_PATH=... node tests/conversation-browser.cjs http://127.0.0.1:5173
const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const base = process.argv[2] || "http://127.0.0.1:5173";
const output =
  process.env.WEB_QA_OUTPUT || path.resolve(__dirname, "../../output/playwright/p5-web");
const settings = {
  dynamicColor: false,
  themeId: "default",
  developerMode: false,
  displaySetting: {
    userNickname: "Fixture",
    showUserAvatar: false,
    showModelName: true,
    showTokenUsage: false,
    showThinkingContent: true,
    autoCloseThinking: false,
    codeBlockAutoWrap: true,
    codeBlockAutoCollapse: false,
    showLineNumbers: false,
    sendOnEnter: true,
    enableAutoScroll: true,
    fontSizeRatio: 1,
    pasteLongTextAsFile: false,
    pasteLongTextThreshold: 1000,
  },
  enableWebSearch: false,
  favoriteModels: [],
  chatModelId: "model",
  assistantId: "assistant",
  providers: [
    {
      id: "provider",
      enabled: true,
      name: "Controlled provider",
      models: [{ id: "model", modelId: "fixture", displayName: "Fixture model", type: "CHAT" }],
    },
  ],
  assistants: [{ id: "assistant", name: "Fixture assistant", tags: [] }],
  assistantTags: [],
  mcpServers: [],
  searchServices: [],
  quickMessages: [],
  searchServiceSelected: 0,
};
const node = (text, role = "USER") => ({
  id: "node-a",
  selectIndex: 0,
  messages: [
    { id: "message-a", role, parts: [{ type: "text", text }], createdAt: "2026-10-18T00:00:00Z" },
  ],
});
const conversation = (id, text = `${id} initial`) => ({
  id,
  assistantId: "assistant",
  title: `Conversation ${id}`,
  messages: [node(text)],
  truncateIndex: 0,
  chatSuggestions: ["Suggested message"],
  isPinned: false,
  createAt: 1,
  updateAt: 2,
  isGenerating: false,
});
const sse = (event, data) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
function deferred() {
  let resolve;
  const promise = new Promise((r) => (resolve = r));
  return { promise, resolve };
}
async function fixture(browser, options = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(() => localStorage.setItem("lang", "en-US"));
  const page = await context.newPage();
  page.setDefaultTimeout(10000);
  const pending = deferred();
  const release = deferred();
  const requests = [];
  const errors = [];
  let fileId = 0;
  const detail = (id, text) => {
    const value = conversation(id, text);
    if (options.attachments)
      value.messages[0].messages[0].parts.push({
        type: "document",
        url: "/api/files/99",
        fileName: "historical.txt",
        mime: "text/plain",
        metadata: { fileId: 99 },
      });
    if (options.code)
      value.messages[0].messages[0].parts = [
        { type: "text", text: "```html\n<h1>Preview fixture</h1>\n```" },
      ];
    if (options.role) value.messages[0].messages[0].role = options.role;
    return value;
  };
  page.on("pageerror", (e) => {
    errors.push(e.message);
    console.error("PAGEERROR", e.message);
  });
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const endpoint = url.pathname.replace("/api/", "");
    const method = request.method();
    requests.push({ method, endpoint, body: request.postData() });
    const json = (body, status = 200) =>
      route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (endpoint === "settings/stream")
      return route.fulfill({ contentType: "text/event-stream", body: sse("message", settings) });
    if (endpoint === "conversations/stream")
      return route.fulfill({ contentType: "text/event-stream", body: ": idle\n\n" });
    if (endpoint === "conversations/paged")
      return json({
        items: ["A", "B"].map((id) => conversation(id)),
        nextOffset: null,
        hasMore: false,
      });
    if (/^conversations\/[^/]+\/stream$/.test(endpoint)) {
      const id = endpoint.split("/")[1];
      return route.fulfill({
        contentType: "text/event-stream",
        body: sse("snapshot", {
          type: "snapshot",
          seq: 1,
          conversation: detail(id, options.race ? "NEW SSE CONTENT" : `${id} initial`),
          serverTime: Date.now(),
        }),
      });
    }
    if (/^conversations\/[^/]+$/.test(endpoint) && method === "GET") {
      const id = endpoint.split("/")[1];
      if (options.race && id === "A") {
        pending.resolve();
        await release.promise;
      }
      return json(detail(id));
    }
    if (/^conversations\/[^/]+\/messages(?:\/[^/]+\/edit)?$/.test(endpoint) && method === "POST") {
      pending.resolve();
      if (options.delayPost) await release.promise;
      return options.failPost
        ? json({ error: "Controlled send failure", code: 400 }, 400)
        : json({ status: "ok" });
    }
    if (endpoint === "files/upload") {
      if (options.delayUpload) {
        pending.resolve();
        await release.promise;
      }
      const name = /filename="([^"]+)"/.exec(request.postData() || "")?.[1] || `file-${fileId}.txt`;
      return json({
        files: [
          {
            id: ++fileId,
            url: `/api/files/${fileId}`,
            fileName: name,
            mime: "text/plain",
            size: 3,
          },
        ],
      });
    }
    if (endpoint.startsWith("files/") && method === "DELETE") {
      if (options.failDelete) return json({ error: "Controlled delete failure", code: 400 }, 400);
      if (options.delayDelete && endpoint === "files/2") {
        pending.resolve();
        await release.promise;
      }
      return json({ status: "ok" });
    }
    return json({ status: "ok" });
  });
  await page.goto(`${base}${options.home ? "/" : "/c/A"}`);
  const input = page.locator("textarea");
  try {
    await input.waitFor();
    await input.waitFor({ state: "visible" });
  } catch (e) {
    console.error("PAGEBODY", await page.locator("body").innerText());
    await page.screenshot({ path: path.join(output, "startup-error.png") });
    await context.close();
    throw e;
  }
  await page.waitForFunction(() => !document.querySelector("textarea")?.disabled);
  return { context, page, input, pending, release, requests, errors };
}
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const results = [];
  async function run(name, options, body) {
    if (process.env.WEB_QA_CASES && !new RegExp(process.env.WEB_QA_CASES).test(name)) return;
    const f = await fixture(browser, options);
    try {
      let timeout;
      try {
        await Promise.race([
          body(f),
          new Promise((_, reject) => {
            timeout = setTimeout(
              () => reject(new Error("Controlled case exceeded 25 seconds")),
              25000,
            );
          }),
        ]);
      } finally {
        clearTimeout(timeout);
      }
      assert.deepEqual(f.errors, []);
      results.push({ name, status: "PASS" });
    } catch (e) {
      results.push({ name, status: "FAIL", error: e.message });
      await f.page.screenshot({ path: path.join(output, `${name}.png`), fullPage: true });
    } finally {
      f.release.resolve();
      await f.context.close();
    }
  }
  try {
    await run("draft-A-B-A", {}, async ({ page, input }) => {
      await input.fill("A draft");
      await page.getByRole("button", { name: "Conversation B", exact: true }).click();
      await input.fill("B draft");
      await page.getByRole("button", { name: "Conversation A", exact: true }).click();
      await page.waitForTimeout(100);
      assert.equal(await input.inputValue(), "A draft");
    });
    await run(
      "send-preserves-new-input",
      { delayPost: true },
      async ({ input, pending, release, page }) => {
        await input.fill("submitted");
        await input.press("Enter");
        await pending.promise;
        await input.fill("new input while sending");
        release.resolve();
        await page.waitForTimeout(400);
        assert.equal(await input.inputValue(), "new input while sending");
      },
    );
    await run(
      "home-failure-retains-visible-draft",
      { home: true, delayPost: true, failPost: true },
      async ({ input, pending, release, page }) => {
        await input.fill("retry this home draft");
        await input.press("Enter");
        await pending.promise;
        release.resolve();
        await page.getByText("Controlled send failure", { exact: true }).waitFor();
        assert.equal(await input.inputValue(), "retry this home draft");
      },
    );
    await run("late-REST-keeps-SSE", { race: true }, async ({ pending, release, page }) => {
      await pending.promise;
      await page.getByText("NEW SSE CONTENT", { exact: true }).waitFor();
      release.resolve();
      await page.waitForTimeout(400);
      assert.equal(await page.getByText("NEW SSE CONTENT", { exact: true }).count(), 1);
    });
    await run(
      "remove-retains-correct-attachment",
      { delayDelete: true },
      async ({ page, pending, release }) => {
        for (const name of ["first.txt", "second.txt", "third.txt"]) {
          await page
            .locator("input[type=file]")
            .first()
            .setInputFiles({ name, mimeType: "text/plain", buffer: Buffer.from("abc") });
          await page.getByText(name, { exact: true }).waitFor();
        }
        const chip = (name) => page.getByText(name, { exact: true }).locator("..");
        await chip("second.txt").getByRole("button").click();
        await pending.promise;
        await chip("first.txt").getByRole("button").click();
        await page.getByText("first.txt", { exact: true }).waitFor({ state: "hidden" });
        release.resolve();
        await page.waitForTimeout(400);
        assert.equal(await page.getByText("second.txt", { exact: true }).count(), 0);
        assert.equal(await page.getByText("third.txt", { exact: true }).count(), 1);
      },
    );
    await run(
      "send-A-completion-keeps-B-draft",
      { delayPost: true },
      async ({ page, input, pending, release }) => {
        await input.fill("A submitted");
        await input.press("Enter");
        await pending.promise;
        await page.getByRole("button", { name: "Conversation B", exact: true }).click();
        await input.fill("B stays");
        release.resolve();
        await page.waitForTimeout(400);
        assert.equal(await input.inputValue(), "B stays");
        await page.getByRole("button", { name: "Conversation A", exact: true }).click();
        await page.waitForTimeout(100);
        assert.equal(await input.inputValue(), "");
      },
    );
    await run(
      "home-success-preserves-new-input",
      { home: true, delayPost: true },
      async ({ input, page, pending, release, requests }) => {
        await input.fill("first home message");
        await input.press("Enter");
        await pending.promise;
        await input.fill("second home draft");
        release.resolve();
        await page.waitForTimeout(400);
        assert.equal(await input.inputValue(), "second home draft");
        assert.equal(new URL(page.url()).pathname, "/");
        assert.equal(
          JSON.parse(requests.find((r) => r.method === "POST").body).parts[0].text,
          "first home message",
        );
      },
    );
    await run(
      "upload-A-completion-stays-owned-by-A",
      { delayUpload: true },
      async ({ page, pending, release }) => {
        await page
          .locator("input[type=file]")
          .first()
          .setInputFiles({
            name: "owned-by-A.txt",
            mimeType: "text/plain",
            buffer: Buffer.from("abc"),
          });
        await pending.promise;
        await page.getByRole("button", { name: "Conversation B", exact: true }).click();
        release.resolve();
        await page.waitForTimeout(400);
        assert.equal(await page.getByText("owned-by-A.txt", { exact: true }).count(), 0);
        await page.getByRole("button", { name: "Conversation A", exact: true }).click();
        await page.getByText("owned-by-A.txt", { exact: true }).waitFor();
      },
    );
    await run("delete-failure-retains-attachment", { failDelete: true }, async ({ page }) => {
      await page
        .locator("input[type=file]")
        .first()
        .setInputFiles({ name: "keep.txt", mimeType: "text/plain", buffer: Buffer.from("abc") });
      await page.getByText("keep.txt", { exact: true }).waitFor();
      await page.getByText("keep.txt", { exact: true }).locator("..").getByRole("button").click();
      await page.getByText("Controlled delete failure", { exact: true }).waitFor();
      assert.equal(await page.getByText("keep.txt", { exact: true }).count(), 1);
    });
    for (const role of ["USER", "ASSISTANT"])
      await run(
        `edit-${role}-historical-file-protection`,
        { attachments: true, role },
        async ({ page, input, requests }) => {
          await page.getByRole("button", { name: "Edit message", exact: true }).click();
          await input.fill("edited body");
          await page
            .getByText("historical.txt", { exact: true })
            .last()
            .locator("..")
            .getByRole("button")
            .click();
          await input.press("Enter");
          await page.waitForTimeout(400);
          const post = requests.find((r) => r.method === "POST" && r.endpoint.endsWith("/edit"));
          assert(post);
          assert.deepEqual(JSON.parse(post.body).parts, [{ type: "text", text: "edited body" }]);
          assert.equal(requests.filter((r) => r.method === "DELETE").length, 0);
        },
      );
    await run("edit-cancel-clears-composer", {}, async ({ page, input }) => {
      await page.getByRole("button", { name: "Edit message", exact: true }).click();
      await input.fill("cancel this");
      await page.getByRole("button", { name: "Cancel Edit", exact: true }).click();
      assert.equal(await input.inputValue(), "");
    });
    for (const width of [1280, 390, 320])
      await run(`workbench-${width}`, { code: true, role: "ASSISTANT" }, async ({ page }) => {
        await page.setViewportSize({ width, height: 844 });
        await page.getByRole("button", { name: "Preview code", exact: true }).click();
        await page.frameLocator("iframe").getByText("Preview fixture", { exact: true }).waitFor();
        await page.waitForTimeout(400);
        await page.screenshot({
          path: path.join(output, `workbench-${width}.png`),
          fullPage: true,
        });
        await page.getByRole("button", { name: "Close panel", exact: true }).click();
        await page.locator("iframe").waitFor({ state: "detached" });
      });
    await run("desktop-baseline", {}, async ({ page }) => {
      await page.screenshot({ path: path.join(output, "desktop-baseline.png"), fullPage: true });
    });
    for (const width of [390, 320])
      await run(`mobile-${width}`, {}, async ({ page }) => {
        await page.setViewportSize({ width, height: 844 });
        await page.waitForTimeout(300);
        await page.screenshot({ path: path.join(output, `mobile-${width}.png`), fullPage: true });
        assert(await page.locator("textarea").isVisible());
      });
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(output, "results.json"), JSON.stringify(results, null, 2));
  }
  console.log(JSON.stringify(results, null, 2));
  if (results.some((r) => r.status === "FAIL")) process.exitCode = 1;
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
