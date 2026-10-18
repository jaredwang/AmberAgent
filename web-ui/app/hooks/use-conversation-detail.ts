import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { toConversationSummaryUpdate } from "~/hooks/use-conversation-list";
import api, { sse } from "~/services/api";
import { useAppStore } from "~/stores";
import type {
  ConversationDto,
  MessageNodeDto,
  ConversationNodeUpdateEventDto,
  ConversationErrorEventDto,
  ConversationSnapshotEventDto,
} from "~/types";

type ConversationStreamEvent =
  | ConversationSnapshotEventDto
  | ConversationNodeUpdateEventDto
  | ConversationErrorEventDto;
export interface SelectedNodeMessage {
  node: MessageNodeDto;
  message: MessageNodeDto["messages"][number];
}
type ConversationSummaryUpdater = (update: ReturnType<typeof toConversationSummaryUpdate>) => void;

export function applyNodeUpdate(
  conversation: ConversationDto,
  event: ConversationNodeUpdateEventDto,
): ConversationDto {
  if (conversation.id !== event.conversationId) {
    return conversation;
  }

  const nextNodes = [...conversation.messages];
  const indexById = nextNodes.findIndex((node) => node.id === event.nodeId);
  const targetIndex = indexById >= 0 ? indexById : event.nodeIndex;

  if (targetIndex < 0) {
    return conversation;
  }

  if (targetIndex < nextNodes.length) {
    nextNodes[targetIndex] = event.node;
  } else if (targetIndex === nextNodes.length) {
    nextNodes.push(event.node);
  } else {
    return conversation;
  }

  return {
    ...conversation,
    messages: nextNodes,
    updateAt: event.updateAt,
    isGenerating: event.isGenerating,
  };
}

export function useConversationDetail(
  activeId: string | null,
  updateSummary: ConversationSummaryUpdater,
) {
  const { t } = useTranslation("page");
  const [detail, setDetail] = React.useState<ConversationDto | null>(null);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [detailError, setDetailError] = React.useState<string | null>(null);

  const resetDetail = React.useCallback(() => {
    setDetail(null);
    setDetailError(null);
    setDetailLoading(false);
  }, []);

  React.useEffect(() => {
    if (!activeId) {
      resetDetail();
      return;
    }

    let mounted = true;
    let receivedStreamUpdate = false;
    setDetailLoading(true);
    setDetailError(null);

    const abortController = new AbortController();

    api
      .get<ConversationDto>(`conversations/${activeId}`)
      .then((data) => {
        if (!mounted || receivedStreamUpdate) return;
        setDetail(data);
        updateSummary(toConversationSummaryUpdate(data));
      })
      .catch((err: Error) => {
        if (!mounted || receivedStreamUpdate) return;
        setDetailError(err.message || t("conversations.errors.load_detail_failed"));
        setDetail(null);
      })
      .finally(() => {
        if (!mounted) return;
        setDetailLoading(false);
      });

    void sse<ConversationStreamEvent>(
      `conversations/${activeId}/stream`,
      {
        onMessage: ({ event, data }) => {
          if (!mounted) return;

          if (event === "error" && data.type === "error") {
            toast.error(data.message);
            return;
          }

          if (event === "snapshot" && data.type === "snapshot") {
            receivedStreamUpdate = true;
            useAppStore.getState().setClockOffset(data.serverTime);
            setDetail(data.conversation);
            updateSummary(toConversationSummaryUpdate(data.conversation));
            setDetailError(null);
            setDetailLoading(false);
            return;
          }

          if (event !== "node_update" || data.type !== "node_update") return;

          if (data.conversationId !== activeId) return;
          useAppStore.getState().setClockOffset(data.serverTime);
          setDetail((prev) => {
            if (!prev) return prev;
            const next = applyNodeUpdate(prev, data);
            if (next === prev) return prev;
            receivedStreamUpdate = true;
            if (prev.isGenerating !== next.isGenerating) {
              updateSummary(toConversationSummaryUpdate(next));
            }
            return next;
          });
          setDetailError(null);
          setDetailLoading(false);
        },
        onError: (streamError) => {
          if (!mounted) return;
          console.error("Conversation detail SSE error:", streamError);
        },
      },
      { signal: abortController.signal },
    );

    return () => {
      mounted = false;
      abortController.abort();
    };
  }, [activeId, resetDetail, t, updateSummary]);

  const selectedNodeMessages = React.useMemo<SelectedNodeMessage[]>(() => {
    if (!detail) return [];
    return detail.messages.map((node) => ({
      node,
      message: node.messages[node.selectIndex] ?? node.messages[0],
    }));
  }, [detail]);

  return {
    detail,
    detailLoading,
    detailError,
    selectedNodeMessages,
    resetDetail,
  };
}
