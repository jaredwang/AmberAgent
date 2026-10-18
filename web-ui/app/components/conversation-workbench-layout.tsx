import * as React from "react";
import type { PanelImperativeHandle } from "react-resizable-panels";
import { Drawer, DrawerContent } from "~/components/ui/drawer";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "~/components/ui/resizable";
import { WorkbenchHost } from "~/components/workbench/workbench-host";
import { useWorkbench } from "~/components/workbench/workbench-context";
import { useIsMobile } from "~/hooks/use-mobile";
import { cn } from "~/lib/utils";

export function ConversationWorkbenchLayout({ children }: { children: React.ReactNode }) {
  const isMobile = useIsMobile();
  const { panel, closePanel } = useWorkbench();
  const hasWorkbenchPanel = Boolean(panel);
  const workbenchPanelRef = React.useRef<PanelImperativeHandle | null>(null);
  React.useEffect(() => {
    if (isMobile) return;
    const workbenchPanel = workbenchPanelRef.current;
    if (!workbenchPanel) return;
    if (hasWorkbenchPanel) workbenchPanel.expand();
    else workbenchPanel.collapse();
  }, [hasWorkbenchPanel, isMobile]);
  return (
    <>
      {!isMobile ? (
        <ResizablePanelGroup orientation="horizontal" className="min-h-0 flex-1">
          <ResizablePanel
            defaultSize={hasWorkbenchPanel ? 64 : 100}
            minSize={40}
            className="flex min-h-0 flex-col"
          >
            {children}
          </ResizablePanel>
          <ResizableHandle
            withHandle
            className={cn(!hasWorkbenchPanel && "pointer-events-none opacity-0")}
          />
          <ResizablePanel
            defaultSize={hasWorkbenchPanel ? 36 : 0}
            minSize={24}
            collapsible
            collapsedSize={0}
            panelRef={workbenchPanelRef}
            className="flex min-h-0 flex-col"
          >
            {panel ? (
              <WorkbenchHost panel={panel} onClose={closePanel} className="border-l-0" />
            ) : null}
          </ResizablePanel>
        </ResizablePanelGroup>
      ) : (
        children
      )}
      {isMobile && panel ? (
        <Drawer
          open={hasWorkbenchPanel}
          onOpenChange={(open) => {
            if (!open) closePanel();
          }}
          direction="bottom"
        >
          <DrawerContent className="h-[85vh] max-h-[85vh]">
            <WorkbenchHost panel={panel} onClose={closePanel} className="border-l-0" />
          </DrawerContent>
        </Drawer>
      ) : null}
    </>
  );
}
