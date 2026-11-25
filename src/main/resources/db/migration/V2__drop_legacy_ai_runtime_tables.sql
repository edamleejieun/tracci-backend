-- Remove legacy Spring Boot AI runtime tables (now owned by tracci-ai-platform).
-- Idempotent: safe when tables were never created or already dropped.

DROP TABLE IF EXISTS ai_plan_quality_scores;
DROP TABLE IF EXISTS ai_plan_quality_signals;
DROP TABLE IF EXISTS ai_usage_records;
DROP TABLE IF EXISTS ai_suggestions;
DROP TABLE IF EXISTS ai_generation_tasks;
DROP TABLE IF EXISTS destination_route_templates;
DROP TABLE IF EXISTS destination_poi_cache;
DROP TABLE IF EXISTS destination_profiles;
DROP TABLE IF EXISTS ai_tool_calls;
DROP TABLE IF EXISTS ai_plan_patches;
DROP TABLE IF EXISTS ai_plan_messages;
DROP TABLE IF EXISTS ai_plan_conversations;
DROP TABLE IF EXISTS ai_plan_generation_events;
DROP TABLE IF EXISTS ai_plan_generation_runs;
DROP TABLE IF EXISTS ai_plan_drafts;