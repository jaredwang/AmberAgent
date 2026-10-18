import * as React from "react";
import { v4 as uuidv4 } from "uuid";
import { useNavigate } from "react-router";

import { useConversationAttachments } from "~/hooks/use-conversation-attachments";
import {
  buildEditedParts,
  stripEditDraftMetadata,
  toEditDraft,
  type EditingSession,
} from "~/lib/conversation-message-edit";
import api from "~/services/api";
import { useChatInputStore } from "~/stores";
import type { MessageDto, UIMessagePart } from "~/types";

const EMPTY_ATTACHMENTS: UIMessagePart[] = [];
export const createHomeDraftId = () => `home-${uuidv4()}`;

export function useConversationComposer({
  activeId,
  isHomeRoute,
  homeDraftId,
  setHomeDraftId,
  navigate,
  refreshList,
}: {
  activeId: string | null;
  isHomeRoute: boolean;
  homeDraftId: string;
  setHomeDraftId: React.Dispatch<React.SetStateAction<string>>;
  navigate: ReturnType<typeof useNavigate>;
  refreshList: () => void;
}) {
  const draftKey = activeId ?? (isHomeRoute ? homeDraftId : null);
  const draft = useChatInputStore(
    React.useCallback((state) => (draftKey ? state.drafts[draftKey] : undefined), [draftKey]),
  );
  const [editingSession, setEditingSession] = React.useState<
    (EditingSession & { draftKey: string }) | null
  >(null);
  React.useEffect(() => {
    setEditingSession(null);
  }, [activeId]);
  const currentDraftKey = React.useRef(draftKey);
  currentDraftKey.current = draftKey;
  const { uploadFiles, removePart } = useConversationAttachments(draftKey);

  const clearEditingSession = React.useCallback((key: string, expected?: EditingSession | null) => {
    setEditingSession((session) =>
      session?.draftKey === key && (!expected || session === expected) ? null : session,
    );
  }, []);

  const handleInputTextChange = React.useCallback(
    (text: string) => {
      if (draftKey) useChatInputStore.getState().setText(draftKey, text);
    },
    [draftKey],
  );

  const handleStartEdit = React.useCallback(
    (message: MessageDto) => {
      if (!activeId || !draftKey || (message.role !== "USER" && message.role !== "ASSISTANT"))
        return;
      const next = toEditDraft(message);
      if (!next) return;
      setEditingSession({
        draftKey,
        messageId: message.id,
        sourceParts: next.sourceParts,
        textPartIndex: next.textPartIndex,
      });
      const store = useChatInputStore.getState();
      store.clearDraft(draftKey);
      store.setText(draftKey, next.text);
      store.addParts(draftKey, next.attachments);
    },
    [activeId, draftKey],
  );

  const handleCancelEdit = React.useCallback(() => {
    if (!draftKey) return;
    clearEditingSession(draftKey);
    useChatInputStore.getState().clearDraft(draftKey);
  }, [clearEditingSession, draftKey]);

  const handleClickSuggestion = React.useCallback(
    (text: string) => {
      if (!draftKey) return;
      clearEditingSession(draftKey);
      useChatInputStore.getState().setText(draftKey, text);
    },
    [clearEditingSession, draftKey],
  );

  const handleSend = React.useCallback(async () => {
    if (!draftKey) return;
    const store = useChatInputStore.getState();
    const submittedDraft = store.drafts[draftKey];
    const parts = store.getSubmitParts(draftKey);
    if (parts.length === 0) return;
    const conversationId = activeId ?? uuidv4();
    const endpoint = editingSession
      ? `conversations/${conversationId}/messages/${editingSession.messageId}/edit`
      : `conversations/${conversationId}/messages`;
    const submittedParts = editingSession ? buildEditedParts(editingSession, parts) : parts;
    await api.post<{ status: string }>(endpoint, {
      parts: editingSession ? stripEditDraftMetadata(submittedParts) : submittedParts,
    });
    const unchanged = useChatInputStore.getState().drafts[draftKey] === submittedDraft;
    if (unchanged) {
      useChatInputStore.getState().clearDraft(draftKey);
      clearEditingSession(draftKey, editingSession);
    }
    if (!activeId) {
      refreshList();
      // Only rotate and navigate if this successful request still owns the visible draft.
      if (unchanged && currentDraftKey.current === draftKey) {
        setHomeDraftId(createHomeDraftId());
        navigate(`/c/${conversationId}`);
      }
    }
  }, [
    activeId,
    clearEditingSession,
    draftKey,
    editingSession,
    navigate,
    refreshList,
    setHomeDraftId,
  ]);

  return {
    draftKey,
    inputText: draft?.text ?? "",
    inputAttachments: draft?.parts ?? EMPTY_ATTACHMENTS,
    editingSession,
    handleInputTextChange,
    handleStartEdit,
    handleCancelEdit,
    handleClickSuggestion,
    handleSend,
    uploadFiles,
    removePart,
  };
}
