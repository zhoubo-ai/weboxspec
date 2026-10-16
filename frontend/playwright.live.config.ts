import { defineConfig } from '@playwright/test';
import base from './playwright.config';
if(!process.env.OPENROUTER_API_KEY)throw new Error('OPENROUTER_API_KEY is required for real-model browser acceptance.');
export default defineConfig({...base,testIgnore:[],testMatch:'**/recommendations-live.spec.ts',fullyParallel:false,workers:1,projects:base.projects!.filter(project=>project.name==='chromium-1440'),timeout:900_000});
