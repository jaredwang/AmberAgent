import * as React from "react";

import { detectUploadFile, getPartFileId, toMessagePart } from "~/lib/conversation-attachments";
import { shouldDeleteAttachmentFileOnRemove } from "~/lib/conversation-message-edit";
import api from "~/services/api";
import { useChatInputStore } from "~/stores";
import type { UIMessagePart, UploadFilesResponseDto } from "~/types";

export function useConversationAttachments(draftKey: string | null) {
  const uploadFiles = React.useCallback(
    async (files: globalThis.File[]) => {
      if (!draftKey) return { skipped: 0 };
      const detected = await Promise.all(
        files.map(async (file) => ({ file, ...(await detectUploadFile(file)) })),
      );
      const accepted = detected.filter((file) => file.allowed);
      const skipped = detected.length - accepted.length;
      if (accepted.length === 0) return { skipped };
      const formData = new FormData();
      for (const { file, mimeType } of accepted) {
        const safeFile =
          file.type === mimeType
            ? file
            : new globalThis.File([file], file.name, { type: mimeType });
        formData.append("files", safeFile, safeFile.name);
      }
      const response = await api.postMultipart<UploadFilesResponseDto>("files/upload", formData);
      // The callback captures the destination before any MIME or network await.
      useChatInputStore.getState().addParts(draftKey, response.files.map(toMessagePart));
      return { skipped };
    },
    [draftKey],
  );

  const removePart = React.useCallback(
    async (_index: number, part: UIMessagePart) => {
      if (!draftKey) return;
      const fileId = getPartFileId(part);
      if (fileId !== null && shouldDeleteAttachmentFileOnRemove(part)) {
        await api.delete<{ status: string }>(`files/${fileId}`);
      }
      const store = useChatInputStore.getState();
      const index = store.drafts[draftKey]?.parts.indexOf(part) ?? -1;
      if (index >= 0) store.removePartAt(draftKey, index);
    },
    [draftKey],
  );

  return { uploadFiles, removePart };
}
