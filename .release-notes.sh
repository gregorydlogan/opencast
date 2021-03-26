#!/bin/bash

version=$(xmllint --xpath '/*[local-name()="project"]/*[local-name()="version"]/text()' pom.xml)

if [[ "$version" == *"SNAPSHOT"* ]]; then
  echo "Which Opencast version?"
  read version
fi

major=$(echo $version | cut -f 1 -d ".")
minor=$(echo $version | cut -f 2 -d ".")
if [[ ! $minor =~ ^-?[0-9]+$ ]]; then
  echo "This script only works properly with x.y versions (8.4, 9.2, etc)"
  exit 1
fi  
last=$(($minor - 1))

start="Opencast $major\: Release Notes"
versx="Additional [nN]otes [aA]bout"
end="Release Schedule"

if [ $minor -gt 0 -a $last -gt 0 ]; then #Matches versions like 8.4, and 9.2
  start="$versx $major.$minor"
  end="$versx $major.$last"
elif [ $minor -eq 1 ]; then #Matches 8.1, and 9.1
  start="$versx $major.$minor"
#elif [ $minor -eq 0 ]; then #matches 8.0, and 9.0
  #pass
fi

regex='/^'$start'/,${p;/'$end'/q}'
sed -n -E "$regex" docs/guides/admin/docs/releasenotes.md | sed '$d'
