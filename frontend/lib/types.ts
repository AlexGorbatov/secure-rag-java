export interface CurrentUser {
  subject: string;
  username: string;
  groups: string[];
  roles: string[];
}

export interface DocumentSummary {
  id: string;
  title: string;
  contentType: string;
  sizeBytes: number;
  status: "PROCESSING" | "READY" | "FAILED";
  chunkCount: number;
  allowedGroups: string[];
  createdAt: string;
}

export interface Citation {
  documentId: string;
  title: string;
}

export interface AnswerResponse {
  answer: string;
  citations: Citation[];
}
