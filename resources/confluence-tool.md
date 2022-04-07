# confl

Simple Confluence CLI client.

# Dependencies
 * [jq](https://stedolan.github.io/jq/) command is required.
 * xmllint command is required.

# Setup

Set following environment variables on your `~/.bashrc` or `~/.zshrc`.

```sh
# Username of the confluence. (i.e: taro.yamada)
export CONFL_USER=XXX

# Password of the confluence.
export CONFL_PASS=XXX

# Space name of your team.
export CONFL_SPACE_KEY=XXX

# API endpoint of your Confluence (i.e: https://localhost:8080/confluence/rest/api/content)
export CONFL_API_END=XXX
```

# Usage

```sh
$ ./confluence-tool.sh
Usage:
  confluence-tool.sh [COMMANDS] [argument ...]

COMMANDS:
  help                            -- Show this help.
  info <PAGE_ID>                  -- Print information the page having given PAGE_ID.
  cat <PAGE_ID> [OPTIONS]         -- Print html of the page having given PAGE_ID.
  ls <PAGE_ID>                    -- Show child pages under the page.
  rm  <PAGE_ID>                   -- Remove the page.
  create <PARENT PAGE_ID> <TITLE> -- Create new page, standard input will be page body.
  update <PARENT PAGE_ID>         -- Update the page, standard input will be page body.

OPTIONS:
  -f  -- pretty print Html
```

# FAQ

* How to get PAGE_ID?
  + See https://confluence.atlassian.com/confkb/how-to-cat-confluence-page-id-648380445.html

