#!/bin/bash

## source: https://medium.com/@gswallow/govc-e66655ee2ede

TEMPLATE_FOLDER=${TEMPLATE_FOLDER:-/templates}
PRUNE_PATTERN=${PRUNE_PATTERN:-XXXXXXX}
KEEP=${KEEP:-3}
templates=($(govc find "${TEMPLATE_FOLDER}" -name "${PRUNE_PATTERN}*" -type m -disabledMethod PowerOnVM_Task | sort))
limit=$[${#templates[@]} - $KEEP]
for template in "${templates[@]:0:$limit}"; do
  if [[ "$(govc vm.info -json "$template" | jq '.VirtualMachines[].Config.Annotation')" =~ keep ]]; then
  echo "keeping $template since it has been held"
  else
echo "pruning $template"
govc vm.destroy -vm.ipath="$template"
fi
done

