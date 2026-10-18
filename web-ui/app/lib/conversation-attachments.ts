import { fileTypeFromBuffer } from "file-type";
import type { UIMessagePart, UploadFilesResponseDto } from "~/types";

export async function detectUploadFile(
  file: globalThis.File,
): Promise<{ allowed: boolean; mimeType: string }> {
  const buffer = await file.slice(0, 4100).arrayBuffer();
  const detected = await fileTypeFromBuffer(buffer);

  // 无法识别 magic bytes → 文本文件 → 允许，强制 text/plain 防止 OS MIME 映射污染（如 .ts → video/mp2t）
  if (!detected) return { allowed: true, mimeType: "text/plain" };

  // 识别为图片 / 视频 / 音频 → 允许，使用 magic bytes 检测到的 MIME
  if (
    detected.mime.startsWith("image/") ||
    detected.mime.startsWith("video/") ||
    detected.mime.startsWith("audio/")
  ) {
    return { allowed: true, mimeType: detected.mime };
  }

  // 允许常见文档格式
  const ALLOWED_DOCUMENT_MIMES = new Set([
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  ]);
  if (ALLOWED_DOCUMENT_MIMES.has(detected.mime)) {
    return { allowed: true, mimeType: detected.mime };
  }

  // 其他可识别的二进制格式（exe、zip 等）→ 拒绝
  return { allowed: false, mimeType: detected.mime };
}

export function toMessagePart(file: UploadFilesResponseDto["files"][number]): UIMessagePart {
  if (file.mime.startsWith("image/")) {
    return {
      type: "image",
      url: file.url,
      metadata: { fileId: file.id },
    };
  }

  if (file.mime.startsWith("video/")) {
    return {
      type: "video",
      url: file.url,
      metadata: { fileId: file.id },
    };
  }

  if (file.mime.startsWith("audio/")) {
    return {
      type: "audio",
      url: file.url,
      metadata: { fileId: file.id },
    };
  }

  return {
    type: "document",
    url: file.url,
    fileName: file.fileName,
    mime: file.mime,
    metadata: { fileId: file.id },
  };
}

export function getPartFileId(part: UIMessagePart): number | null {
  const value = part.metadata?.fileId;
  return typeof value === "number" ? value : null;
}
