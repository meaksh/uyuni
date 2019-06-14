mgr_virt_destroy:
  mgrcompat.module_run:
    - name: virt.purge
    - vm_: {{ pillar['domain_name'] }}
