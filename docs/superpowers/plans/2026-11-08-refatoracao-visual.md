# Refatoração Visual (Unslop) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refatorar a UI do `App.kt` / `SpaceHomeScreen` para um design moderno, limpo e escuro, extraindo lógica de interface para componentes modulares, mantendo 100% da lógica de negócio.

**Architecture:** 
1. Auditoria e mapeamento de componentes.
2. Modularização de UI (`SpaceHeader`, `SpaceSidebar`, `SpaceHomeScreen`).
3. Refinamento de design (Dark Mode moderno, tipografia e espaçamento).

**Tech Stack:** Jetpack Compose, Material3.

**Spec:** Baseado na solicitação de "unslop completo" e "Dark Mode moderno".

## Global Constraints
- **Preservar:** `rememberAccountState()`, `rememberProfiles()`, `createSourceRepository()`, `rememberLibraryStore()`, `rememberComments()`, etc.
- **Preservar:** `SpaceScreen` enum, `HomeNavFocus` enum.
- **Visual:** Dark Mode moderno, espaçamentos consistentes (grid 4px), tipografia legível.

## Review Focus
- A lógica de autenticação ainda funciona?
- O estado da biblioteca (`LibraryStore`) é atualizado corretamente?
- A navegação entre `SpaceScreen` continua disparando os eventos originais?
- O Dark Mode está aplicado em todos os elementos?
- Os espaçamentos (`padding`) estão consistentes?

---

### Task 1: Auditoria e Estruturação de Componentes
- [ ] Mapear blocos de código em `App.kt` que pertencem à UI.
- [ ] Criar arquivo `composeApp/src/commonMain/kotlin/app/anim/HomeScreen.kt`.

### Task 2: Modularização
- [ ] Mover `SpaceHomeScreen` e seus auxiliares para o novo arquivo.
- [ ] Manter `App.kt` como o orquestrador principal de estado.

### Task 3: Polimento Visual (Unslop)
- [ ] Aplicar espaçamento 4px.
- [ ] Ajustar Dark Mode (palette, contrast).

### Task 4: Verificação
- [ ] Validar navegação e estados.
