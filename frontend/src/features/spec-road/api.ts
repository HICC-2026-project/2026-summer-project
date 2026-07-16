import { apiFetch } from "@/lib/api";
import type { UserMeResponse } from "./types";

export function getMe(): Promise<UserMeResponse> {
  return apiFetch<UserMeResponse>("/api/v1/users/me");
}
