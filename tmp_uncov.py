import xml.etree.ElementTree as ET
t = ET.parse('tiger-coverage/target/site/jacoco-aggregate/jacoco.xml').getroot()
targets = {
    'CanopyServer.java', 'CanopyDnsServer.java', 'ProxyAddressProvider.java',
    'CanopyAdminClient.java', 'CanopyGlue.java', 'SystemDnsResolver.java',
    'ApiExceptionHandler.java', 'CanopyDnsHealthIndicator.java',
    'ResolverChain.java', 'TigerProxyAdminClient.java',
}
for pkg in t.findall('.//package'):
    if 'canopy' not in pkg.get('name'):
        continue
    for sf in pkg.findall('sourcefile'):
        if sf.get('name') not in targets:
            continue
        missed = [ln.get('nr') for ln in sf.findall('line') if int(ln.get('mi')) > 0]
        partial = [ln.get('nr') for ln in sf.findall('line')
                   if int(ln.get('mi')) == 0 and int(ln.get('mb')) > 0]
        print(f"\n=== {pkg.get('name')}/{sf.get('name')} ===")
        print(f"missed lines ({len(missed)}): {','.join(missed)}")
        print(f"partial-branch lines ({len(partial)}): {','.join(partial)}")

