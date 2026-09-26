# Architecture Overview

## 1. Services
The platform is a single Spring Boot service backed by PostgreSQL with the pgvector extension.

## 2. Access control
Every request carries a JWT; document access is filtered inside the database query, never after.

## 3. Deployment
The service and its database run as containers; there is no separate search cluster.
