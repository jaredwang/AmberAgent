import * as React from "react";
import { MessageSquare } from "lucide-react";
import { useTranslation } from "react-i18next";
import {
  ConversationQuickJump,
  getConversationMessageAnchorId,
} from "~/components/conversation-quick-jump";
import {
  Conversation,
  ConversationContent,
  ConversationEmptyState,
  ConversationScrollButton,
} from "~/components/extended/conversation";
import { ChatMessage } from "~/components/message/chat-message";
import { TypingIndicator } from "~/components/ui/typing-indicator";
import type { SelectedNodeMessage } from "~/hooks/use-conversation-detail";
import { cn } from "~/lib/utils";
import type { MessageDto, ProviderModel, Settings, UIMessagePart } from "~/types";

function truncatePreviewText(value: string, maxLength = 48): string {
  if (value.length <= maxLength) {
    return value;
  }

  return `${value.slice(0, maxLength)}...`;
}

function getQuickJumpPreview(
  message: MessageDto,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  const textPreview = message.parts
    .filter((part): part is Extract<UIMessagePart, { type: "text" }> => part.type === "text")
    .map((part) => part.text.trim())
    .find((text) => text.length > 0);

  if (textPreview) {
    return truncatePreviewText(textPreview.replace(/\s+/g, " "));
  }

  const fallbackPart = message.parts.find(Boolean);
  if (!fallbackPart) return t("conversations.preview.empty_message");

  switch (fallbackPart.type) {
    case "image":
      return t("conversations.preview.image");
    case "video":
      return t("conversations.preview.video");
    case "audio":
      return t("conversations.preview.audio");
    case "document":
      return fallbackPart.fileName.trim().length > 0
        ? t("conversations.preview.document_with_name", {
            name: truncatePreviewText(fallbackPart.fileName.trim(), 32),
          })
        : t("conversations.preview.document");
    case "reasoning":
      return fallbackPart.reasoning.trim().length > 0
        ? truncatePreviewText(fallbackPart.reasoning.trim().replace(/\s+/g, " "))
        : t("conversations.preview.thinking");
    case "tool":
      return fallbackPart.toolName.trim().length > 0
        ? t("conversations.preview.tool_with_name", {
            name: truncatePreviewText(fallbackPart.toolName.trim(), 32),
          })
        : t("conversations.preview.tool_call");
    case "text":
      return t("conversations.preview.empty_message");
  }
}

export const ConversationTimeline = React.memo(
  ({
    activeId,
    isHomeRoute,
    detailLoading,
    detailError,
    selectedNodeMessages,
    isGenerating,
    settings,
    conversationAssistantId,
    contentClassName,
    onEdit,
    onDelete,
    onFork,
    onRegenerate,
    onSelectBranch,
    onToolApproval,
  }: {
    activeId: string | null;
    isHomeRoute: boolean;
    detailLoading: boolean;
    detailError: string | null;
    selectedNodeMessages: SelectedNodeMessage[];
    isGenerating: boolean;
    settings: Settings | null;
    conversationAssistantId: string | null;
    contentClassName?: string;
    onEdit: (message: MessageDto) => void | Promise<void>;
    onDelete: (messageId: string) => Promise<void>;
    onFork: (messageId: string) => Promise<void>;
    onRegenerate: (messageId: string) => Promise<void>;
    onSelectBranch: (nodeId: string, selectIndex: number) => Promise<void>;
    onToolApproval: (
      toolCallId: string,
      approved: boolean,
      reason: string,
      answer?: string,
    ) => Promise<void>;
  }) => {
    const { t } = useTranslation("page");
    const canQuickJump =
      Boolean(activeId) && !detailLoading && !detailError && selectedNodeMessages.length > 1;
    const assistant = React.useMemo(() => {
      if (!settings || !conversationAssistantId) return null;
      return settings.assistants.find((item) => item.id === conversationAssistantId) ?? null;
    }, [conversationAssistantId, settings]);
    const modelById = React.useMemo(() => {
      const map = new Map<string, ProviderModel>();
      if (!settings) return map;

      for (const provider of settings.providers) {
        for (const model of provider.models) {
          if (!map.has(model.id)) {
            map.set(model.id, model);
          }
        }
      }

      return map;
    }, [settings]);

    return (
      <Conversation className="flex-1 min-h-0">
        <ConversationContent
          className={cn("mx-auto w-full max-w-3xl gap-4 px-4 py-6", contentClassName)}
        >
          {!activeId && !isHomeRoute && (
            <ConversationEmptyState
              icon={<MessageSquare className="size-10" />}
              title={t("conversations.empty_state.select_title")}
              description={t("conversations.empty_state.select_description")}
            />
          )}
          {activeId && detailLoading && (
            <ConversationEmptyState
              title={t("conversations.empty_state.loading_title")}
              description={t("conversations.empty_state.loading_description")}
            />
          )}
          {activeId && detailError && (
            <ConversationEmptyState
              title={t("conversations.empty_state.error_title")}
              description={detailError}
            />
          )}
          {!detailLoading && !detailError && activeId && selectedNodeMessages.length === 0 && (
            <ConversationEmptyState
              icon={<MessageSquare className="size-10" />}
              title={t("conversations.empty_state.no_message_title")}
              description={t("conversations.empty_state.no_message_description")}
            />
          )}
          {!detailLoading &&
            !detailError &&
            activeId &&
            selectedNodeMessages.map(({ node, message }, index) => {
              const model = message.modelId ? (modelById.get(message.modelId) ?? null) : null;

              return (
                <div
                  key={message.id}
                  id={getConversationMessageAnchorId(message.id)}
                  className="scroll-mt-24"
                >
                  <ChatMessage
                    node={node}
                    message={message}
                    loading={isGenerating && index === selectedNodeMessages.length - 1}
                    isLastMessage={index === selectedNodeMessages.length - 1}
                    assistant={assistant}
                    model={model}
                    onEdit={onEdit}
                    onDelete={onDelete}
                    onFork={onFork}
                    onRegenerate={onRegenerate}
                    onSelectBranch={onSelectBranch}
                    onToolApproval={onToolApproval}
                  />
                </div>
              );
            })}
          {!detailLoading && !detailError && activeId && isGenerating && (
            <div className="flex items-start py-2">
              <TypingIndicator className="px-1 py-2" />
            </div>
          )}
        </ConversationContent>

        {canQuickJump ? (
          <ConversationQuickJump
            items={selectedNodeMessages.map(({ message }) => ({
              id: message.id,
              role: message.role,
              preview: getQuickJumpPreview(message, t),
            }))}
          />
        ) : null}

        <ConversationScrollButton />
      </Conversation>
    );
  },
);
