import { describe, expect, test } from "bun:test";
import {
  buildEditedParts,
  shouldDeleteAttachmentFileOnRemove,
  stripEditDraftMetadata,
  toEditDraft,
} from "../app/lib/conversation-message-edit";
const message = (role) => ({
  id: "message",
  role,
  createdAt: "2026-10-18",
  parts: [
    { type: "text", text: "first text", metadata: { keep: 1 } },
    { type: "reasoning", reasoning: "preserved reasoning" },
    {
      type: "tool",
      toolName: "tool",
      toolCallId: "call",
      input: "{}",
      output: [],
      approvalState: { type: "approved" },
    },
    { type: "image", url: "image", metadata: { fileId: 1 } },
    { type: "text", text: "last text", metadata: { keep: 2 } },
    {
      type: "document",
      url: "document",
      fileName: "source.pdf",
      mime: "application/pdf",
      metadata: { fileId: 2 },
    },
  ],
});
describe("conversation message editor", () => {
  for (const role of ["USER", "ASSISTANT"])
    test(`${role} edit preserves nonedited parts and source order`, () => {
      const source = message(role);
      const draft = toEditDraft(source);
      expect(draft.text).toBe("last text");
      const result = buildEditedParts(
        {
          messageId: source.id,
          sourceParts: draft.sourceParts,
          textPartIndex: draft.textPartIndex,
        },
        [{ type: "text", text: "changed" }, ...draft.attachments],
      );
      expect(result).toEqual(
        source.parts.map((part, index) => (index === 4 ? { ...part, text: "changed" } : part)),
      );
      expect(stripEditDraftMetadata(result)).toEqual(result);
      expect(source.parts[4]).toEqual({ type: "text", text: "last text", metadata: { keep: 2 } });
    });
  test("historical attachment removal preserves its file; new uploads can delete", () => {
    const source = message("USER");
    const draft = toEditDraft(source);
    const newFile = { type: "audio", url: "new", metadata: { fileId: 3 } };
    expect(draft.attachments.every((part) => !shouldDeleteAttachmentFileOnRemove(part))).toBe(true);
    expect(shouldDeleteAttachmentFileOnRemove(newFile)).toBe(true);
    const result = buildEditedParts(
      { messageId: source.id, sourceParts: draft.sourceParts, textPartIndex: draft.textPartIndex },
      [{ type: "text", text: "edited" }, draft.attachments[1], newFile],
    );
    expect(result.some((part) => part.type === "image")).toBe(false);
    expect(result.at(-2)).toBe(source.parts[5]);
    expect(result.at(-1)).toBe(newFile);
  });
  test("wire submission strips editor metadata and preserves file metadata", () => {
    const draft = toEditDraft(message("ASSISTANT"));
    expect(stripEditDraftMetadata(draft.attachments)).toEqual([
      message("ASSISTANT").parts[3],
      message("ASSISTANT").parts[5],
    ]);
  });
  test("attachment-only edit can prepend text while preserving nontext parts", () => {
    const source = {
      ...message("USER"),
      parts: [
        { type: "reasoning", reasoning: "keep" },
        { type: "image", url: "old" },
      ],
    };
    const draft = toEditDraft(source);
    expect(
      buildEditedParts(
        {
          messageId: source.id,
          sourceParts: draft.sourceParts,
          textPartIndex: draft.textPartIndex,
        },
        [{ type: "text", text: "added" }, ...draft.attachments],
      ),
    ).toEqual([{ type: "text", text: "added" }, ...source.parts]);
  });
});
