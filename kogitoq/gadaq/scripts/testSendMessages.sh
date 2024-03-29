#!/bin/bash
if [  "$#" -lt 3 ]; then
	echo "Usage: $0 <templateCode> <recipientCode> [port:9580] [realm:alyson]"
	echo "E.g: $0 MSG_TEMP_PASSWORD PER_0F6169E1-FDD5-4DAF-BEC3-4126C6626752 6590 lojing"
fi
messageTemplateCode="${1:-MSG_TEMP_PASSWORD}"
recipientBeCode="${2:-PER_0F6169E1-FDD5-4DAF-BEC3-4126C6626752}"
port="${3:-6590}"
realm="${4:-lojing}"
echo  "Getting token for realm.... ${realm}"
TOKEN=`./gettoken-prod.sh ${realm}`
echo ''
echo $TOKEN
echo ''
curl -v -H "Content-Type: application/json"  -H "Accept: application/json" -H "Authorization: Bearer $TOKEN" -X POST http://alyson2.genny.life:${port}/sendMessageTest -d @- << EOF
{
        "templateCode" : "${messageTemplateCode}",
        "recipientBECode"     : "${recipientBeCode}"
}
EOF
echo ""
