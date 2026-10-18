import * as React from "react";

import { useNavigate, useParams } from "react-router";

import { ConversationSidebar } from "~/components/conversation-sidebar";
import { ConversationTimeline } from "~/components/conversation-timeline";
import { ConversationWorkbenchLayout } from "~/components/conversation-workbench-layout";
import Logo from "~/components/logo";
import { ChatInput } from "~/components/input/chat-input";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "~/components/ui/sidebar";
import { useConversationList } from "~/hooks/use-conversation-list";
import { useConversationDetail } from "~/hooks/use-conversation-detail";
import { createHomeDraftId, useConversationComposer } from "~/hooks/use-conversation-composer";
import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { useCurrentModel } from "~/hooks/use-current-model";
import { getAssistantDisplayName, getModelDisplayName } from "~/lib/display";
import { convertConversationToMarkdown, downloadMarkdown } from "~/lib/export-markdown";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import {
  useWorkbench,
  useWorkbenchController,
  WorkbenchProvider,
} from "~/components/workbench/workbench-context";
import { useTranslation } from "react-i18next";
import i18n from "~/i18n";

const EMPTY_SUGGESTIONS: string[] = [];

export function meta() {
  return [
    { title: i18n.t("page:conversations.meta.title") },
    {
      name: "description",
      content: i18n.t("page:conversations.meta.description"),
    },
  ];
}

export default function ConversationsPage() {
  const workbench = useWorkbenchController();

  return (
    <WorkbenchProvider value={workbench}>
      <ConversationsPageInner />
    </WorkbenchProvider>
  );
}

function ConversationsPageInner() {
  const { t } = useTranslation("page");
  const navigate = useNavigate();
  const { id: routeId } = useParams();
  const isHomeRoute = !routeId;
  const { closePanel } = useWorkbench();

  const { settings, assistants, currentAssistantId, currentAssistant } = useCurrentAssistant();
  const { currentModel, currentProvider } = useCurrentModel();
  const {
    conversations,
    activeId,
    setActiveId,
    loading,
    error,
    hasMore,
    loadMore,
    refreshList,
    updateConversationSummary,
  } = useConversationList({ currentAssistantId, routeId, autoSelectFirst: !isHomeRoute });

  const [homeDraftId, setHomeDraftId] = React.useState(() => createHomeDraftId());

  const { detail, detailLoading, detailError, selectedNodeMessages, resetDetail } =
    useConversationDetail(activeId, updateConversationSummary);

  const {
    draftKey,
    inputText,
    inputAttachments,
    editingSession,
    handleInputTextChange,
    handleStartEdit,
    handleCancelEdit,
    handleClickSuggestion,
    handleSend,
    uploadFiles,
    removePart,
  } = useConversationComposer({
    activeId,
    isHomeRoute,
    homeDraftId,
    setHomeDraftId,
    navigate,
    refreshList,
  });

  const activeConversation = conversations.find((item) => item.id === activeId);
  const chatSuggestions = detail?.chatSuggestions ?? EMPTY_SUGGESTIONS;

  React.useEffect(() => {
    const base = t("conversations.meta.title");
    document.title = activeConversation?.title ? `${activeConversation.title} - ${base}` : base;
    return () => {
      document.title = base;
    };
  }, [activeConversation?.title, t]);
  const isNewChat = isHomeRoute && !activeId;
  const showSuggestions =
    Boolean(activeId) && !detailLoading && !detailError && chatSuggestions.length > 0;
  const displaySuggestions = showSuggestions ? chatSuggestions : EMPTY_SUGGESTIONS;

  const handleSelect = React.useCallback(
    (id: string) => {
      setActiveId(id);
      if (routeId !== id) {
        navigate(`/c/${id}`);
      }
    },
    [navigate, routeId, setActiveId],
  );

  const handleAssistantChange = React.useCallback(
    async (assistantId: string) => {
      await api.post<{ status: string }>("settings/assistant", { assistantId });
      setActiveId(null);
      resetDetail();
      if (routeId) {
        navigate("/", { replace: true });
      }
      refreshList();
    },
    [navigate, refreshList, resetDetail, routeId, setActiveId],
  );

  const handleToolApproval = React.useCallback(
    async (toolCallId: string, approved: boolean, reason: string, answer?: string) => {
      if (!activeId) return;
      await api.post<{ status: string }>(`conversations/${activeId}/tool-approval`, {
        toolCallId,
        approved,
        reason,
        ...(answer != null ? { answer } : {}),
      });
    },
    [activeId],
  );

  const handleRegenerate = React.useCallback(
    async (messageId: string) => {
      if (!activeId) return;
      await api.post<{ status: string }>(`conversations/${activeId}/regenerate`, {
        messageId,
      });
    },
    [activeId],
  );

  const handleSelectBranch = React.useCallback(
    async (nodeId: string, selectIndex: number) => {
      if (!activeId) return;
      await api.post<{ status: string }>(`conversations/${activeId}/nodes/${nodeId}/select`, {
        selectIndex,
      });
    },
    [activeId],
  );

  const handleDeleteMessage = React.useCallback(
    async (messageId: string) => {
      if (!activeId) return;
      await api.delete<{ status: string }>(`conversations/${activeId}/messages/${messageId}`);
    },
    [activeId],
  );

  const handleForkMessage = React.useCallback(
    async (messageId: string) => {
      if (!activeId) return;
      const response = await api.post<{ conversationId: string }>(
        `conversations/${activeId}/fork`,
        {
          messageId,
        },
      );
      setActiveId(response.conversationId);
      navigate(`/c/${response.conversationId}`);
      refreshList();
    },
    [activeId, navigate, refreshList, setActiveId],
  );

  const handleTogglePinConversation = React.useCallback(
    async (conversationId: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/pin`);
      refreshList();
    },
    [refreshList],
  );

  const handleRegenerateConversationTitle = React.useCallback(
    async (conversationId: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/regenerate-title`);
      refreshList();
    },
    [refreshList],
  );

  const handleMoveConversation = React.useCallback(
    async (conversationId: string, assistantId: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/move`, { assistantId });
      if (conversationId === activeId) {
        setActiveId(null);
        resetDetail();
        setHomeDraftId(createHomeDraftId());
        if (routeId === conversationId) {
          navigate("/", { replace: true });
        }
      }
      refreshList();
    },
    [activeId, navigate, refreshList, resetDetail, routeId, setActiveId],
  );

  const handleUpdateConversationTitle = React.useCallback(
    async (conversationId: string, title: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/title`, { title });
      refreshList();
    },
    [refreshList],
  );

  const handleDeleteConversation = React.useCallback(
    async (conversationId: string) => {
      await api.delete<Record<string, never>>(`conversations/${conversationId}`, {
        parseJson: (raw) => (raw ? JSON.parse(raw) : {}),
      });
      if (conversationId === activeId) {
        setActiveId(null);
        resetDetail();
        setHomeDraftId(createHomeDraftId());
        if (routeId === conversationId) {
          navigate("/", { replace: true });
        }
      }
      refreshList();
    },
    [activeId, navigate, refreshList, resetDetail, routeId, setActiveId],
  );

  const handleCreateConversation = React.useCallback(() => {
    closePanel();
    setActiveId(null);
    resetDetail();
    setHomeDraftId(createHomeDraftId());

    if (routeId) {
      navigate("/");
    }
  }, [closePanel, navigate, resetDetail, routeId, setActiveId]);

  const handleStop = React.useCallback(async () => {
    if (!activeId) return;
    await api.post<{ status: string }>(`conversations/${activeId}/stop`);
  }, [activeId]);

  const chatContent = (
    <div
      className={cn("flex flex-1 flex-col min-h-0 overflow-hidden", isNewChat && "justify-center")}
    >
      {!isNewChat && (
        <div className="relative flex min-h-0 flex-1">
          <ConversationTimeline
            activeId={activeId}
            isHomeRoute={isHomeRoute}
            detailLoading={detailLoading}
            detailError={detailError}
            selectedNodeMessages={selectedNodeMessages}
            isGenerating={detail?.isGenerating ?? false}
            settings={settings}
            conversationAssistantId={detail?.assistantId ?? null}
            onEdit={handleStartEdit}
            onDelete={handleDeleteMessage}
            onFork={handleForkMessage}
            onRegenerate={handleRegenerate}
            onSelectBranch={handleSelectBranch}
            onToolApproval={handleToolApproval}
          />
        </div>
      )}

      <div>
        {isNewChat && (
          <div className="mb-4 text-center">
            <div className="mb-3 flex justify-center">
              <div className="[&>svg]:size-16">
                <Logo className="size-16 text-primary" />
              </div>
            </div>
            <p className="text-lg text-muted-foreground">{t("conversations.welcome_prompt")}</p>
          </div>
        )}
        <ChatInput
          value={inputText}
          attachments={inputAttachments}
          ready={draftKey !== null}
          isGenerating={detail?.isGenerating ?? false}
          disabled={detailLoading || Boolean(detailError)}
          onValueChange={handleInputTextChange}
          onUploadFiles={uploadFiles}
          suggestions={displaySuggestions}
          onSuggestionClick={handleClickSuggestion}
          isEditing={Boolean(editingSession)}
          onCancelEdit={editingSession ? handleCancelEdit : undefined}
          onRemovePart={removePart}
          onSend={handleSend}
          onStop={activeId ? handleStop : undefined}
          onExportConversation={
            detail && detail.messages.length > 0
              ? (includeReasoning: boolean) => {
                  const content = convertConversationToMarkdown(detail, includeReasoning);
                  const filename = `${detail.title || "conversation"}.md`;
                  downloadMarkdown(content, filename);
                }
              : undefined
          }
        />
      </div>
    </div>
  );

  return (
    <SidebarProvider defaultOpen className="h-svh overflow-hidden">
      <ConversationSidebar
        conversations={conversations}
        activeId={activeId}
        loading={loading}
        error={error}
        hasMore={hasMore}
        loadMore={loadMore}
        userName={
          settings?.displaySetting.userNickname?.trim() || t("conversations.user.default_name")
        }
        userAvatar={settings?.displaySetting.userAvatar}
        assistants={assistants}
        assistantTags={settings?.assistantTags ?? []}
        currentAssistantId={currentAssistantId}
        onSelect={handleSelect}
        onAssistantChange={handleAssistantChange}
        onPin={handleTogglePinConversation}
        onRegenerateTitle={handleRegenerateConversationTitle}
        onMoveToAssistant={handleMoveConversation}
        onUpdateTitle={handleUpdateConversationTitle}
        onDelete={handleDeleteConversation}
        onCreateConversation={handleCreateConversation}
        webAuthEnabled={settings?.webServerJwtEnabled === true}
      />
      <SidebarInset className="flex min-h-svh flex-col overflow-hidden">
        <div className="flex items-center gap-2 border-b px-4 py-3">
          <SidebarTrigger />
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm text-muted-foreground">
              {activeConversation
                ? activeConversation.title
                : t("conversations.header.select_conversation")}
            </div>
            {currentModel && currentProvider ? (
              <div className="truncate text-xs text-muted-foreground/80">
                {`${getAssistantDisplayName(currentAssistant?.name)} / ${getModelDisplayName(currentModel.displayName, currentModel.modelId)} (${currentProvider.name})`}
              </div>
            ) : null}
          </div>
        </div>

        <ConversationWorkbenchLayout>{chatContent}</ConversationWorkbenchLayout>
      </SidebarInset>
    </SidebarProvider>
  );
}
