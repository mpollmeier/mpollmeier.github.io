---
layout: post
title: github actions - trigger one workflow from another
date: 2025-05-16
permalink: 2025/5/github-actions-trigger-one-workflow-from-another
tags: [github-actions, ci]
---

If your builds run on github actions and you want to trigger one workflow from another, you might first believe that you can simply push a tag in workflow A and the have workflow B get triggered by pushed tags. Github actions specifically deactivates that kind of workflow triggering though, to avoid recursive or unintended workflow runs, as documented [here](https://docs.github.com/en/actions/writing-workflows/choosing-when-your-workflow-runs/triggering-a-workflow#triggering-a-workflow-from-a-workflow). This is [surprising for many](https://github.com/orgs/community/discussions/27028), as it was for me. 

Workaround: you can trigger another workflow using the github action [REST API](https://docs.github.com/en/rest/actions/workflows?apiVersion=2022-11-28#create-a-workflow-dispatch-event), e.g. like so:

workflow-a.yml:
```
jobs:
  trigger_workflow_b:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - run: git tag v0.0.1 # replace with your regular automatic release handling
      - name: Store tag in env var
        run: echo "LATEST_TAG=$(git describe --tags --abbrev=0)" >> $GITHUB_ENV
      - name: Trigger Workflow B
        run: |
          curl -X POST \
            -H "Accept: application/vnd.github+json" \
            -H "Authorization: Bearer $\\{\{ secrets.GITHUB_TOKEN \}\}" \
            https://api.github.com/repos/${{ github.repository }}/actions/workflows/workflow-b.yml/dispatches \
            -d '{"ref":"${{ env.LATEST_TAG }}"}'
```

workflow-b.yml:
```yaml
on:
  workflow_dispatch:

jobs:
  run_job:
    runs-on: ubuntu-latest
    steps:
      - name: Say hello
        run: echo "Workflow B triggered by Workflow A, using ref ${{ github.ref_name }}"
```
