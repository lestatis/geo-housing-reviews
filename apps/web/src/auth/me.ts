import { serverApi } from "@/src/api/client";

/**
 * The signed-in administrator's own account id.
 *
 * <p>Needed so the account screen can tell "remove this person's access" from "step down". The
 * principal name is the opaque account id, but this app never decodes the token itself — it asks
 * the API, which is the only thing entitled to interpret it.
 */
export async function meAccountId(): Promise<string | undefined> {
  const me = await (await serverApi()).GET("/api/me", {});
  return me.data?.accountId;
}
