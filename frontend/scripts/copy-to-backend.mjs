import { cp, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..");
const sourceDir = path.join(projectRoot, "out");
const targetDir = path.resolve(projectRoot, "..", "src", "main", "resources", "static");

if (!existsSync(sourceDir)) {
  throw new Error(`Expected a static export at ${sourceDir}. Run "next build" first.`);
}

await rm(targetDir, { recursive: true, force: true });
await cp(sourceDir, targetDir, { recursive: true });

console.log(`Copied ${sourceDir} -> ${targetDir}`);
