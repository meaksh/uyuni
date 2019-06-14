startssh:
    mgrcompat.module_run:
    -   name: mgractionchains.start
    -   actionchain_id: {{ pillar.get('actionchain_id')}}
