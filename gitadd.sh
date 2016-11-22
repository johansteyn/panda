#!/bin/bash

echo ""
echo "Doing a 'git add' on all modified files..."
echo ""
git ls-files --modified | xargs git add
echo ""
echo "Done."
echo ""

